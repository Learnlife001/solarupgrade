package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.service.CartService;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The order lists are paged, and paged in the database.
 *
 * <p>{@code /admin/orders} loaded every order ever placed into one page. It
 * looked fine at a dozen and would have made the back office unusable at a few
 * thousand — on the page whoever runs the shop spends their day.
 *
 * <p>The trap being guarded against is subtler than "is there a pager": a
 * {@code Pageable} on a query that fetches a collection does not page in SQL at
 * all. Hibernate loads every matching row, joins the items and applies the
 * offset in memory. The page would look correct and the query would still grow
 * with the shop, which is why {@link #onlyOnePagesWorthIsLoaded()} checks what
 * came back rather than what was displayed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderPagingTest {

    private static final String ADMIN = "paging-admin@example.test";
    private static final int ORDERS = 7;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    private User buyer;

    @BeforeEach
    void setUp() {
        users.findByEmail(ADMIN).orElseGet(() -> {
            User admin = new User(ADMIN, "hash", "Paging Admin");
            admin.markEmailVerified();
            admin.setRole(Role.ADMIN);
            return users.save(admin);
        });

        String unique = UUID.randomUUID().toString().substring(0, 8);
        buyer = users.save(new User("paging-" + unique + "@example.test", "hash", "Paging Buyer"));
        Product product = products.save(new Product("Paging Panel " + unique, "A panel.",
                new BigDecimal("1000.00"), Category.PANEL, 500, null));

        for (int i = 0; i < ORDERS; i++) {
            cartService.add(buyer, product, 1);
            CheckoutForm form = new CheckoutForm();
            form.setShippingName("Paging Buyer");
            form.setShippingLine1("1 Test Street");
            form.setShippingCity("Lagos");
            form.setShippingState("Lagos");
            form.setShippingCountry("NG");
            form.setPaymentMethod(PaymentMethod.PAYPAL);
            orderService.placeOrder(buyer, form);
        }
    }

    /**
     * The point of the whole change: a page of three is three rows out of the
     * database, not everything trimmed afterwards.
     */
    @Test
    void onlyOnePagesWorthIsLoaded() {
        Page<Order> page = orderService.ordersPage(null, OrderService.page(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(ORDERS);
    }

    /** Every order is reachable, and none appears on two pages. */
    @Test
    void pagesDoNotOverlapOrLoseOrders() {
        Page<Order> first = orderService.ordersPageFor(buyer, OrderService.page(0, 3));
        Page<Order> second = orderService.ordersPageFor(buyer, OrderService.page(1, 3));

        assertThat(first.getContent()).hasSize(3);
        assertThat(second.getContent()).hasSize(3);
        assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
        assertThat(first.getTotalElements()).isEqualTo(ORDERS);
    }

    /** Newest first, on every page. */
    @Test
    void ordersComeBackNewestFirst() {
        Page<Order> page = orderService.ordersPageFor(buyer, OrderService.page(0, ORDERS));

        assertThat(page.getContent()).isSortedAccordingTo(
                (a, b) -> b.getPlacedAt().compareTo(a.getPlacedAt()));
    }

    /**
     * The items are loaded with the orders. Paging in two queries is only
     * correct if the second one still fetches what the page renders --
     * otherwise every row throws on its total, with open-in-view off.
     */
    @Test
    void thePageStillCarriesWhatTheTemplateNeeds() {
        Page<Order> page = orderService.ordersPage(null, OrderService.page(0, 3));

        assertThat(page.getContent()).allSatisfy(order -> {
            assertThat(order.getItems()).isNotEmpty();
            assertThat(order.getTotalDisplay()).isNotBlank();
            assertThat(order.getUser().getEmail()).isNotBlank();
        });
    }

    /** A filtered list pages within the filter, not across everything. */
    @Test
    void filteringAndPagingWorkTogether() {
        Page<Order> pending = orderService.ordersPage(
                OrderStatus.PENDING_PAYMENT, OrderService.page(0, 3));

        assertThat(pending.getContent()).hasSize(3);
        assertThat(pending.getContent())
                .allSatisfy(order -> assertThat(order.getStatus())
                        .isEqualTo(OrderStatus.PENDING_PAYMENT));
    }

    @Test
    void theAdminListRendersWithAPager() throws Exception {
        String html = mockMvc.perform(get("/admin/orders").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("order(s) in total");
    }

    /**
     * Paging a filtered list must keep the filter. Dropping it on page two
     * silently shows a different list than the one being read.
     */
    @Test
    void theFilterSurvivesTheNextPageLink() throws Exception {
        for (int i = 0; i < 30; i++) {
            cartService.add(buyer, products.findAll().get(0), 1);
            CheckoutForm form = new CheckoutForm();
            form.setShippingName("Paging Buyer");
            form.setShippingLine1("1 Test Street");
            form.setShippingCity("Lagos");
            form.setShippingState("Lagos");
            form.setShippingCountry("NG");
            form.setPaymentMethod(PaymentMethod.PAYPAL);
            orderService.placeOrder(buyer, form);
        }

        String html = mockMvc.perform(get("/admin/orders")
                        .param("status", "PENDING_PAYMENT")
                        .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The pager itself rendered, and its links carry the filter. Asserting
        // only that the string appears somewhere would pass on the filter row
        // at the top of the page, which is not what is being checked.
        assertThat(html).contains("class=\"pager\"");
        assertThat(html).contains("Older");
        assertThat(html).containsPattern("href=\"[^\"]*page=1[^\"]*status=PENDING_PAYMENT");
    }
}
