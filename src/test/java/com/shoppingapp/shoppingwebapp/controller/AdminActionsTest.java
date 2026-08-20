package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.CartItemRepository;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.StockMovementRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.EmailService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional.
 *
 * <p>These started out transactional and passed while the real screens threw
 * "Entity not managed": a test transaction keeps every entity attached across
 * calls, so a service that mutated an order loaded by an earlier transaction
 * looked fine here and failed in a browser. A test of an admin action has to
 * span transactions the way the request does, so the rows are cleaned up by
 * hand instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminActionsTest {

    private static final String ADMIN = "admin-actions@example.test";
    private static final String BUYER = "admin-actions-buyer@example.test";

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
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @MockitoSpyBean
    private EmailService emailService;

    private User buyer;
    private Product panel;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail(ADMIN)
                .orElseGet(() -> userRepository.save(new User(ADMIN, "hash", "Admin")));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        buyer = userRepository.save(new User(BUYER + System.nanoTime(), "hash", "Buyer"));
        panel = productRepository.save(
                new Product("Admin Panel", "desc", new BigDecimal("250000.00"), Category.PANEL, 10, null));
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

    private OrderStatus statusOf(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    @Test
    void aPaidOrderCanBeShippedAndTheCustomerIsTold() throws Exception {
        Order placed = order(1);
        orderService.markPaid(orderService.getAnyOrder(placed.getId()));

        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/ship")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(placed)).isEqualTo(OrderStatus.SHIPPED);
        verify(emailService).sendOrderShipped(any());
    }

    /**
     * The mistake this guards against: dispatching goods nobody paid for,
     * which from a list of similar-looking rows is one misclick away.
     */
    @Test
    void anUnpaidOrderCannotBeShipped() throws Exception {
        Order placed = order(1);

        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/ship")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(placed)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(emailService, never()).sendOrderShipped(any());
    }

    /** A double-click must not send the customer two dispatch emails. */
    @Test
    void shippingTwiceEmailsOnce() throws Exception {
        Order placed = order(1);
        orderService.markPaid(orderService.getAnyOrder(placed.getId()));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/admin/orders/" + placed.getId() + "/ship")
                    .with(user(ADMIN).roles("ADMIN")).with(csrf()));
        }

        verify(emailService, times(1)).sendOrderShipped(any());
    }

    @Test
    void cancellingAnUnpaidOrderReturnsItsStock() throws Exception {
        Order placed = order(4);
        assertThat(productRepository.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(6);

        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/cancel")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(placed)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(productRepository.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    /**
     * Cancelling a paid order here would hand back stock for goods somebody
     * has already paid for, with no refund to match it.
     */
    @Test
    void aPaidOrderCannotBeCancelledFromTheAdminScreen() throws Exception {
        Order placed = order(4);
        orderService.markPaid(orderService.getAnyOrder(placed.getId()));

        mockMvc.perform(post("/admin/orders/" + placed.getId() + "/cancel")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(statusOf(placed)).isEqualTo(OrderStatus.PAID);
        assertThat(productRepository.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(6);
    }

    @Test
    void stockCanBeSetToACountedFigure() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/stock")
                        .param("stock", "42")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(42);
    }

    @Test
    void negativeStockIsRefused() throws Exception {
        mockMvc.perform(post("/admin/products/" + panel.getId() + "/stock")
                        .param("stock", "-5")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(productRepository.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(10);
    }
}
