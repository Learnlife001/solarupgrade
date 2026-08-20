package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.AdminActionRepository;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refunding through the actual admin page.
 *
 * <p>Separate from {@code RefundTest}, which drives the service. This one goes
 * through the controller, and it is the reason the service now loads the order
 * by id: the page reads the order in one transaction and the refund runs in
 * another, so passing the loaded order along threw "Entity not managed" the
 * moment stock was returned. A service-level test never saw it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRefundTest {

    /** Confirms every refund, so the page is what is under test. */
    static class AlwaysRefunds implements PaymentProvider {

        @Override
        public String id() {
            return "always-refunds";
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
            return new RefundResult(true, "COMPLETED", "REFUND-" + order.getId(),
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
            return new String[]{"x-signature"};
        }

        @Override
        public Optional<PaymentEvent> readWebhook(String rawBody) {
            return Optional.empty();
        }
    }

    @TestConfiguration
    static class WithProvider {

        @Bean
        AlwaysRefunds alwaysRefunds() {
            return new AlwaysRefunds();
        }
    }

    private static final String ADMIN = "refund-admin@example.test";
    private static final String CUSTOMER = "refund-customer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.shoppingapp.shoppingwebapp.service.payment.PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @Autowired
    private AdminActionRepository auditEntries;

    private Order order;

    @BeforeEach
    void setUp() {
        account(ADMIN, Role.ADMIN);
        User customer = account(CUSTOMER, Role.USER);

        String unique = UUID.randomUUID().toString().substring(0, 8);
        Product product = products.save(new Product("Admin Refund Panel " + unique, "A panel.",
                new BigDecimal("100000.00"), Category.PANEL, 10, null));
        cartService.add(customer, product, 1);

        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Test Buyer");
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        order = orderService.placeOrder(customer, form);

        paymentService.begin(order);
        paymentService.complete(order);
    }

    private User account(String email, Role role) {
        return users.findByEmail(email).orElseGet(() -> {
            User created = new User(email, "hash", "Test Person");
            created.markEmailVerified();
            created.setRole(role);
            return users.save(created);
        });
    }

    @Test
    void anAdminCanRefundFromTheOrderPage() throws Exception {
        mockMvc.perform(post("/admin/orders/" + order.getId() + "/refund")
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("reason", "Customer changed their mind"));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
    }

    /**
     * A refund with no reason is refused. The trail is read weeks later by
     * somebody reconciling accounts, and an amount with no account of why is
     * what makes one useless.
     */
    @Test
    void aRefundWithoutAReasonIsRefused() throws Exception {
        mockMvc.perform(post("/admin/orders/" + order.getId() + "/refund")
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("reason", "   "));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void theReasonAndTheAmountAreRecorded() throws Exception {
        mockMvc.perform(post("/admin/orders/" + order.getId() + "/refund")
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("reason", "Damaged in transit"));

        assertThat(auditEntries.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getActor()).isEqualTo(ADMIN);
                    assertThat(entry.getDetail())
                            .contains("Damaged in transit")
                            .contains("100,000");
                });
    }

    @Test
    void aCustomerCannotRefundTheirOwnOrder() throws Exception {
        mockMvc.perform(post("/admin/orders/" + order.getId() + "/refund")
                        .with(user(CUSTOMER)).with(csrf())
                        .param("reason", "I would like my money back"))
                .andExpect(status().isForbidden());

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }
}
