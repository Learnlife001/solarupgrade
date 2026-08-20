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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof that a payment provider can be added by writing one class.
 *
 * <p>This is the test the abstraction exists for. It defines a provider that
 * has nothing to do with PayPal, registers it as a bean, and then drives a
 * whole payment through {@code PaymentService} — which never learns whose
 * provider it is. If somebody later puts a {@code if (method == PAYPAL)} back
 * into the service, this fails.
 *
 * <p>It doubles as the worked example for whoever adds Paystack, Flutterwave
 * or OPay: this class is about sixty lines, and no existing file changes.
 */
@SpringBootTest
@Transactional
class PluggableProviderTest {

    /** A stand-in for a real provider, e.g. a Nigerian one taking naira. */
    static class FakeProvider implements PaymentProvider {

        String startedFor;
        boolean captureSucceeds = true;

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public PaymentMethod method() {
            // BANK_TRANSFER is offered by the shop but has no provider, which
            // makes it the honest slot for one that arrives later.
            return PaymentMethod.BANK_TRANSFER;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public Checkout begin(Order order, String returnUrl, String cancelUrl) {
            startedFor = returnUrl;
            return new Checkout("FAKE-REF-" + order.getId(), "https://fake.example/pay/" + order.getId());
        }

        @Override
        public CaptureResult capture(Order order) {
            return new CaptureResult(captureSucceeds, captureSucceeds ? "COMPLETED" : "DECLINED",
                    order.getPaymentAmount(), order.getPaymentCurrency());
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
            return new String[]{"x-fake-signature"};
        }

        @Override
        public Optional<PaymentEvent> readWebhook(String rawBody) {
            return Optional.empty();
        }
    }

    @TestConfiguration
    static class WithFakeProvider {

        @Bean
        FakeProvider fakeProvider() {
            return new FakeProvider();
        }
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentProviders providers;

    @Autowired
    private FakeProvider fake;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    private Order order;

    @BeforeEach
    void setUp() {
        // The provider is one bean for the whole context, so a test that made
        // it decline would leave it declining for whatever ran next.
        fake.captureSucceeds = true;
        fake.startedFor = null;

        User user = users.save(new User("pluggable@example.test", "hash", "Test Buyer"));
        Product product = products.save(new Product("Provider Test Panel", "A panel.",
                new BigDecimal("100000.00"), Category.PANEL, 5, null));
        cartService.add(user, product, 1);

        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Test Buyer");
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        order = orderService.placeOrder(user, form);
    }

    @Test
    void aNewProviderIsFoundWithoutRegisteringItAnywhere() {
        assertThat(providers.byId("fake")).containsSame(fake);
        assertThat(providers.forMethod(PaymentMethod.BANK_TRANSFER)).containsSame(fake);
    }

    /** A method with a configured provider is live; that is the whole rule. */
    @Test
    void itsMethodBecomesLive() {
        assertThat(paymentService.isLive(PaymentMethod.BANK_TRANSFER)).isTrue();
    }

    @Test
    void paymentStartsThroughItWithoutTheServiceKnowingWhoItIs() {
        String redirect = paymentService.begin(order);

        assertThat(redirect).isEqualTo("https://fake.example/pay/" + order.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getProviderReference())
                .isEqualTo("FAKE-REF-" + order.getId());
    }

    /** The return URL it is handed carries its own id, not PayPal's. */
    @Test
    void theReturnUrlIsBuiltFromTheProvidersOwnId() {
        paymentService.begin(order);

        assertThat(fake.startedFor).contains("/payments/fake/return");
    }

    @Test
    void aSuccessfulCaptureMarksTheOrderPaid() {
        paymentService.begin(order);

        assertThat(paymentService.complete(order)).isTrue();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    /**
     * And a declined one leaves it unpaid. The rule that an order is paid only
     * because a provider said the money moved has to hold for every provider,
     * not just the one that was written first.
     */
    @Test
    void aDeclinedCaptureLeavesTheOrderUnpaid() {
        fake.captureSucceeds = false;
        paymentService.begin(order);

        assertThat(paymentService.complete(order)).isFalse();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
