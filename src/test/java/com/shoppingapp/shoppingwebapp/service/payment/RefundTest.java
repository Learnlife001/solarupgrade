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
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sending money back.
 *
 * <p>Driven through a provider written for the test, because the guarantees
 * being checked are the shop's and not PayPal's: the money is only recorded as
 * returned when the provider says it went, a refund cannot happen twice, and
 * stock comes back only if it never left.
 *
 * <p><b>Not {@code @Transactional}.</b> Returning stock re-reads each product
 * row under a lock, and inside a single test transaction that re-read discards
 * the checkout's own decrement -- which had this test reporting stock going
 * <em>up</em> by two after a refund. Everything here commits, as it does in
 * production. Each test makes its own product and buyer rather than cleaning
 * up, since orders point at both.
 */
@SpringBootTest
class RefundTest {

    /** A provider that refunds, and can be told to fail. */
    static class RefundingProvider implements PaymentProvider {

        boolean refundSucceeds = true;
        int refundCalls;

        @Override
        public String id() {
            return "refunder";
        }

        @Override
        public PaymentMethod method() {
            return PaymentMethod.BANK_TRANSFER;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public Checkout begin(Order order, String returnUrl, String cancelUrl) {
            return new Checkout("REF-" + order.getId(), "https://provider.example/pay");
        }

        @Override
        public CaptureResult capture(Order order) {
            return new CaptureResult(true, "COMPLETED", "CAPTURE-" + order.getId(),
                    order.getPaymentAmount(), order.getPaymentCurrency());
        }

        @Override
        public boolean canRefund() {
            return true;
        }

        @Override
        public RefundResult refund(Order order) {
            refundCalls++;
            return new RefundResult(refundSucceeds, refundSucceeds ? "COMPLETED" : "PENDING",
                    "REFUND-" + order.getId(), order.getPaymentAmount(), order.getPaymentCurrency());
        }

        @Override
        public boolean canVerifyWebhooks() {
            return true;
        }

        @Override
        public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
            return true;
        }

        @Override
        public String[] signatureHeaders() {
            return new String[]{"x-signature"};
        }

        @Override
        public Optional<PaymentEvent> readWebhook(String rawBody) {
            return Optional.empty();
        }
    }

    @TestConfiguration
    static class WithRefundingProvider {

        @Bean
        RefundingProvider refundingProvider() {
            return new RefundingProvider();
        }
    }

    private static final int STARTING_STOCK = 10;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RefundingProvider provider;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    private Order order;
    private Product product;
    private User buyer;

    @BeforeEach
    void setUp() {
        provider.refundSucceeds = true;
        provider.refundCalls = 0;

        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);
        buyer = users.save(new User("refund-" + unique + "@example.test", "hash", "Test Buyer"));
        product = products.save(new Product("Refund Test Panel " + unique, "A panel.",
                new BigDecimal("100000.00"), Category.PANEL, STARTING_STOCK, null));
        cartService.add(buyer, product, 2);

        order = placeOrderFor(buyer);

        // Pay it the way a real one is paid, so the capture id is recorded by
        // the same code path production uses.
        paymentService.begin(order);
        paymentService.complete(order);
    }

    private int stockNow() {
        return products.findById(product.getId()).orElseThrow().getStock();
    }

    /**
     * The capture id is what a refund is made against, and it used to be
     * thrown away. Without it stored, no order could be refunded at all.
     */
    @Test
    void payingRecordsTheCaptureARefundWouldUse() {
        assertThat(orders.findById(order.getId()).orElseThrow().getCaptureReference())
                .isEqualTo("CAPTURE-" + order.getId());
    }

    @Test
    void aPaidOrderCanBeRefunded() {
        assertThat(paymentService.refund(order.getId())).isTrue();

        Order refunded = orders.findById(order.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.getRefundReference()).isEqualTo("REFUND-" + order.getId());
        assertThat(refunded.getRefundedAt()).isNotNull();
    }

    /** Refunding before dispatch puts the units back: they never left. */
    @Test
    void refundingBeforeDispatchReturnsTheStock() {
        assertThat(stockNow()).isEqualTo(STARTING_STOCK - 2);

        paymentService.refund(order.getId());

        assertThat(stockNow()).isEqualTo(STARTING_STOCK);
    }

    /**
     * Refunding after dispatch does not. The goods are with the customer, and
     * inventing them back onto the shelf sells something twice — with the
     * second buyer discovering it.
     */
    @Test
    void refundingAfterDispatchDoesNotReturnStock() {
        orderService.markShipped(order.getId());
        assertThat(stockNow()).isEqualTo(STARTING_STOCK - 2);

        paymentService.refund(order.getId());

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockNow()).isEqualTo(STARTING_STOCK - 2);
    }

    /**
     * A provider that did not confirm has not refunded. Recording it anyway
     * would leave an order claiming money went back when it did not — which
     * the customer finds out and we do not.
     */
    @Test
    void anUnconfirmedRefundChangesNothing() {
        provider.refundSucceeds = false;

        assertThat(paymentService.refund(order.getId())).isFalse();

        Order untouched = orders.findById(order.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(untouched.getRefundReference()).isNull();
        assertThat(stockNow()).isEqualTo(STARTING_STOCK - 2);
    }

    /** A double click must not send the money twice. */
    @Test
    void refundingTwiceOnlyRefundsOnce() {
        paymentService.refund(order.getId());
        paymentService.refund(order.getId());

        assertThat(provider.refundCalls).isEqualTo(1);
        assertThat(stockNow()).isEqualTo(STARTING_STOCK);
    }

    /** Nothing was ever charged on an unpaid order, so there is nothing to send back. */
    @Test
    void anUnpaidOrderCannotBeRefunded() {
        Order unpaid = freshUnpaidOrder();

        assertThatThrownBy(() -> paymentService.refund(unpaid.getId()))
                .isInstanceOf(PaymentException.class);
    }

    private Order freshUnpaidOrder() {
        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);
        User other = users.save(new User("refund-unpaid-" + unique + "@example.test", "hash", "Other Buyer"));
        cartService.add(other, product, 1);
        return placeOrderFor(other);
    }

    private Order placeOrderFor(User user) {
        CheckoutForm form = new CheckoutForm();
        form.setShippingName(user.getFullName());
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        return orderService.placeOrder(user, form);
    }

    /**
     * An order with no capture recorded — paid before this was stored, or
     * through a route that carried none — is refused rather than offered a
     * button that fails at the provider.
     */
    @Test
    void anOrderWithNoCaptureRecordedIsNotRefundable() {
        Order withoutCapture = orders.findById(order.getId()).orElseThrow();
        withoutCapture.setCaptureReference(null);

        assertThat(withoutCapture.isRefundable()).isFalse();
        assertThat(paymentService.canRefund(withoutCapture)).isFalse();
    }
}
