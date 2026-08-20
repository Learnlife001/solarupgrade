package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.AdminAction;
import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.AdminActionRepository;
import com.shoppingapp.shoppingwebapp.repository.CartItemRepository;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.StockMovementRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.AuditService;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional, for the same reason AdminActionsTest is not: these go
 * through the real request path, which spans transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditTest {

    private static final String ADMIN = "audit-admin@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AdminActionRepository adminActions;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AuditService auditService;

    private User buyer;
    private Product panel;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail(ADMIN)
                .orElseGet(() -> userRepository.save(new User(ADMIN, "hash", "Audit Admin")));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        buyer = userRepository.save(
                new User("audit-buyer-" + System.nanoTime() + "@example.test", "hash", "Buyer"));
        panel = productRepository.save(
                new Product("Audit Panel", "desc", new BigDecimal("250000.00"), Category.PANEL, 10, null));
        adminActions.deleteAll();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll(orderRepository.findByUserOrderByPlacedAtDesc(buyer));
        cartItemRepository.deleteAll(cartItemRepository.findByUser(buyer));
        userRepository.delete(buyer);
        // The stock ledger points at the product row, so it goes first. The
        // application never deletes a product -- it archives -- so this is
        // test cleanup rather than something the shop does.
        stockMovementRepository.deleteAll(stockMovementRepository
                .findByProductIdOrderByHappenedAtDescIdDesc(panel.getId(), Limit.of(1000)));
        productRepository.deleteById(panel.getId());
        adminActions.deleteAll();
    }

    private Order order(int quantity) {
        cartService.add(buyer, panel, quantity);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Buyer");
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingCity("Victoria Island");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return orderService.placeOrder(buyer, form);
    }

    @Test
    void cancellingAnOrderIsRecordedWithWhoDidIt() throws Exception {
        Order placed = order(2);

        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/cancel")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        List<AdminAction> history = auditService.forOrder(placed.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getActor()).isEqualTo(ADMIN);
        assertThat(history.get(0).getAction()).isEqualTo(AdminActionType.ORDER_CANCELLED);
        assertThat(history.get(0).getDetail()).contains("returned to stock");
    }

    @Test
    void shippingAnOrderIsRecorded() throws Exception {
        Order placed = order(1);
        orderService.markPaid(orderService.getAnyOrder(placed.getId()));

        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/ship")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        assertThat(auditService.forOrder(placed.getId()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AdminActionType.ORDER_SHIPPED);
                    assertThat(entry.getActor()).isEqualTo(ADMIN);
                });
    }

    /** A stock change records both figures, so the history reads as a change. */
    @Test
    void aStockChangeRecordsWhatItWasAndWhatItBecame() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/stock")
                .param("stock", "42")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        assertThat(auditService.recent(5))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AdminActionType.STOCK_SET);
                    assertThat(entry.getDetail()).contains("10 to 42");
                });
    }

    /**
     * The guard that matters: an action the service refused must leave no
     * record. An audit row for something that did not happen is worse than
     * none, because it states something untrue with confidence.
     */
    @Test
    void aRefusedActionIsNotRecorded() throws Exception {
        Order unpaid = order(1);

        // Shipping an unpaid order is refused.
        mockMvc.perform(post("/admin/orders/" + unpaid.getId() + "/ship")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        assertThat(auditService.forOrder(unpaid.getId())).isEmpty();
    }

    @Test
    void theHistoryAppearsOnTheOrderPage() throws Exception {
        Order placed = order(1);
        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/cancel")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        mockMvc.perform(get("/admin/orders/" + placed.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("History")))
                .andExpect(content().string(containsString(ADMIN)))
                .andExpect(content().string(containsString("Cancelled and stock returned")));
    }

    @Test
    void recentActivityAppearsOnTheDashboard() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/stock")
                .param("stock", "7")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()));

        mockMvc.perform(get("/admin").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recent activity")))
                .andExpect(content().string(containsString("Stock level set")));
    }
}
