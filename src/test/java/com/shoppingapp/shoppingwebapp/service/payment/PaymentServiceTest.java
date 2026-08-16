package com.shoppingapp.shoppingwebapp.service.payment;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.EmailService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PayPal itself is a mock here. This environment cannot reach
 * api-m.sandbox.paypal.com, and even where it could, the questions worth
 * asking are about our side of the exchange: that we ask for the amount we
 * quoted, that we only mark an order paid on a completed capture, and that a
 * mismatched or replayed answer changes nothing.
 */
@SpringBootTest(properties = {
        "app.paypal.client-id=test-client",
        "app.paypal.client-secret=test-secret",
        "app.paypal.webhook-id=test-webhook",
        "app.base-url=https://shop.example"
})
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private PayPalClient payPal;

    @MockitoBean
    private EmailService emailService;

    private User user;
    private Product panel;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("payer@example.test", "hash", "Pay Er"));
        panel = productRepository.save(
                new Product("Pay Panel", "desc", new BigDecimal("380000.00"), Category.PANEL, 50, null));
    }

    private Order paypalOrder() {
        cartService.add(user, panel, 1);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Pay Er");
        form.setShippingLine1("1 Test Close");
        form.setShippingCity("Ikeja");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return orderService.placeOrder(user, form);
    }

    private Order reload(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @Test
    void startingAPaymentAsksPayPalForTheAmountWeQuoted() {
        Order order = paypalOrder();
        when(payPal.createOrder(anyLong(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new PayPalClient.CreatedOrder("PP-1", "https://paypal.example/approve"));

        String approvalUrl = paymentService.beginPayPal(order);

        assertThat(approvalUrl).isEqualTo("https://paypal.example/approve");
        // The snapshot, not a fresh conversion.
        verify(payPal).createOrder(eq(order.getId()), eq(order.getPaymentAmount()), eq("EUR"),
                eq("https://shop.example/payments/paypal/return?order=" + order.getId()),
                eq("https://shop.example/payments/paypal/cancel?order=" + order.getId()));
        assertThat(reload(order.getId()).getProviderReference()).isEqualTo("PP-1");
    }

    @Test
    void aCompletedCaptureMarksTheOrderPaid() {
        Order order = paypalOrder();
        order.setProviderReference("PP-2");
        orderRepository.save(order);
        when(payPal.capture("PP-2")).thenReturn(new PayPalClient.Capture(
                true, "COMPLETED", order.getPaymentAmount(), "EUR"));

        assertThat(paymentService.completePayPal(order, user)).isTrue();
        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void anIncompleteCaptureLeavesTheOrderUnpaid() {
        Order order = paypalOrder();
        order.setProviderReference("PP-3");
        orderRepository.save(order);
        when(payPal.capture("PP-3")).thenReturn(new PayPalClient.Capture(
                false, "DECLINED", null, null));

        assertThat(paymentService.completePayPal(order, user)).isFalse();
        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /**
     * The case that would otherwise ship goods for less than they cost: a
     * capture that completed, but not for the amount we asked for.
     */
    @Test
    void aCaptureForTheWrongAmountIsRefused() {
        Order order = paypalOrder();
        order.setProviderReference("PP-4");
        orderRepository.save(order);
        when(payPal.capture("PP-4")).thenReturn(new PayPalClient.Capture(
                true, "COMPLETED", new BigDecimal("1.00"), "EUR"));

        assertThatThrownBy(() -> paymentService.completePayPal(order, user))
                .isInstanceOf(PaymentException.class);
        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void aCaptureInTheWrongCurrencyIsRefused() {
        Order order = paypalOrder();
        order.setProviderReference("PP-5");
        orderRepository.save(order);
        when(payPal.capture("PP-5")).thenReturn(new PayPalClient.Capture(
                true, "COMPLETED", order.getPaymentAmount(), "USD"));

        assertThatThrownBy(() -> paymentService.completePayPal(order, user))
                .isInstanceOf(PaymentException.class);
        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /** Returning to the page twice must not capture twice. */
    @Test
    void anAlreadyPaidOrderIsNotCapturedAgain() {
        Order order = paypalOrder();
        order.setProviderReference("PP-6");
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        assertThat(paymentService.completePayPal(order, user)).isTrue();
        verify(payPal, never()).capture(anyString());
    }

    @Test
    void aWebhookSettlesTheOrderItNames() {
        Order order = paypalOrder();
        order.setProviderReference("PP-7");
        orderRepository.save(order);

        paymentService.settleFromWebhook(order.getId(), "PP-7", order.getPaymentAmount(), "EUR");

        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    /** Providers retry, so the same notification arriving twice must be safe. */
    @Test
    void aReplayedWebhookChangesNothing() {
        Order order = paypalOrder();
        order.setProviderReference("PP-8");
        orderRepository.save(order);

        paymentService.settleFromWebhook(order.getId(), "PP-8", order.getPaymentAmount(), "EUR");
        paymentService.settleFromWebhook(order.getId(), "PP-8", order.getPaymentAmount(), "EUR");

        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    /**
     * A genuine, correctly signed notification about a different payment must
     * not settle this order.
     */
    @Test
    void aWebhookCarryingAnotherPaymentsReferenceIsIgnored() {
        Order order = paypalOrder();
        order.setProviderReference("PP-9");
        orderRepository.save(order);

        paymentService.settleFromWebhook(order.getId(), "SOMEONE-ELSE", order.getPaymentAmount(), "EUR");

        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void aWebhookReportingTheWrongAmountIsIgnored() {
        Order order = paypalOrder();
        order.setProviderReference("PP-10");
        orderRepository.save(order);

        paymentService.settleFromWebhook(order.getId(), "PP-10", new BigDecimal("0.01"), "EUR");

        assertThat(reload(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void aWebhookForAnUnknownOrderIsIgnoredRatherThanThrowing() {
        paymentService.settleFromWebhook(999_999L, "PP-X", new BigDecimal("1.00"), "EUR");
    }

    /**
     * The customer should hear that their money arrived, once. Both routes
     * settle through the same transition, which is what stops a webhook
     * landing just after a page refresh producing two receipts.
     */
    @Test
    void payingSendsExactlyOneReceipt() {
        Order order = paypalOrder();
        order.setProviderReference("PP-11");
        orderRepository.save(order);
        when(payPal.capture("PP-11")).thenReturn(new PayPalClient.Capture(
                true, "COMPLETED", order.getPaymentAmount(), "EUR"));

        paymentService.completePayPal(order, user);
        // Then the webhook arrives for the same payment, as it will.
        paymentService.settleFromWebhook(order.getId(), "PP-11", order.getPaymentAmount(), "EUR");

        verify(emailService, times(1)).sendPaymentReceived(any(Order.class));
    }

    @Test
    void aRefusedPaymentSendsNoReceipt() {
        Order order = paypalOrder();
        order.setProviderReference("PP-12");
        orderRepository.save(order);
        when(payPal.capture("PP-12")).thenReturn(new PayPalClient.Capture(
                false, "DECLINED", null, null));

        paymentService.completePayPal(order, user);

        verify(emailService, never()).sendPaymentReceived(any(Order.class));
    }

    @Test
    void aWebhookOnlySettlementStillSendsTheReceipt() {
        Order order = paypalOrder();
        order.setProviderReference("PP-13");
        orderRepository.save(order);

        // The buyer paid and closed the tab, so only the webhook reports it.
        paymentService.settleFromWebhook(order.getId(), "PP-13", order.getPaymentAmount(), "EUR");

        verify(emailService, times(1)).sendPaymentReceived(any(Order.class));
    }

    @Test
    void anOrderThatIsNotAwaitingPaymentCannotBeStarted() {
        Order order = paypalOrder();
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        assertThatThrownBy(() -> paymentService.beginPayPal(order))
                .isInstanceOf(PaymentException.class);
    }
}
