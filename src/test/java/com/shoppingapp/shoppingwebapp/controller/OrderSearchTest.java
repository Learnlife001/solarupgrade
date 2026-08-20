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
 * Finding one order among many.
 *
 * <p>Whoever runs the shop has one of three things in front of them when they
 * go looking: a number from an email, the address the customer wrote from, or
 * the name on the parcel. Paging back through a list is not an answer to any of
 * them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderSearchTest {

    private static final String ADMIN = "search-admin@example.test";

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

    private Order adaezeOrder;
    private String adaezeEmail;

    @BeforeEach
    void setUp() {
        users.findByEmail(ADMIN).orElseGet(() -> {
            User admin = new User(ADMIN, "hash", "Search Admin");
            admin.markEmailVerified();
            admin.setRole(Role.ADMIN);
            return users.save(admin);
        });

        String unique = UUID.randomUUID().toString().substring(0, 8);
        Product product = products.save(new Product("Search Panel " + unique, "A panel.",
                new BigDecimal("1000.00"), Category.PANEL, 100, null));

        adaezeEmail = "adaeze-" + unique + "@example.test";
        User adaeze = users.save(new User(adaezeEmail, "hash", "Adaeze Okafor"));
        cartService.add(adaeze, product, 1);
        adaezeOrder = place(adaeze, "Adaeze Okafor");

        User other = users.save(new User("other-" + unique + "@example.test", "hash", "Someone Else"));
        cartService.add(other, product, 1);
        place(other, "Someone Else");
    }

    private Order place(User user, String name) {
        CheckoutForm form = new CheckoutForm();
        form.setShippingName(name);
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return orderService.placeOrder(user, form);
    }

    private Page<Order> search(String term) {
        return orderService.searchOrders(null, term, OrderService.page(0, 20));
    }

    /** The number from the email a customer is quoting back. */
    @Test
    void anOrderIsFoundByItsNumber() {
        assertThat(search(String.valueOf(adaezeOrder.getId())).getContent())
                .extracting(Order::getId)
                .containsExactly(adaezeOrder.getId());
    }

    /** A number that matches nothing is not a partial match on other ids. */
    @Test
    void theNumberSearchIsExactNotAPrefix() {
        assertThat(search("999999").getContent()).isEmpty();
    }

    @Test
    void anOrderIsFoundByTheCustomersEmail() {
        assertThat(search(adaezeEmail).getContent())
                .extracting(Order::getId)
                .contains(adaezeOrder.getId());
    }

    /** Part of an address is enough; nobody types the whole thing. */
    @Test
    void partOfAnEmailIsEnough() {
        assertThat(search("adaeze").getContent())
                .extracting(Order::getId)
                .contains(adaezeOrder.getId());
    }

    @Test
    void anOrderIsFoundByTheNameOnTheParcel() {
        assertThat(search("Okafor").getContent())
                .extracting(Order::getId)
                .contains(adaezeOrder.getId());
    }

    /** Nobody types the capitals the way they were entered. */
    @Test
    void searchingIgnoresCase() {
        assertThat(search("okafor").getContent())
                .extracting(Order::getId)
                .contains(adaezeOrder.getId());
    }

    /**
     * A blank box is not a search for nothing — it is no search at all, and
     * must return the list rather than an empty page.
     */
    @Test
    void aBlankSearchIsNotASearch() {
        assertThat(search("   ").getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(search(null).getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    /** The status filter and the search narrow together, not against each other. */
    @Test
    void aSearchAndAStatusApplyTogether() {
        Page<Order> pending = orderService.searchOrders(
                OrderStatus.PENDING_PAYMENT, "Okafor", OrderService.page(0, 20));
        Page<Order> shipped = orderService.searchOrders(
                OrderStatus.SHIPPED, "Okafor", OrderService.page(0, 20));

        assertThat(pending.getContent()).extracting(Order::getId).contains(adaezeOrder.getId());
        assertThat(shipped.getContent()).isEmpty();
    }

    @Test
    void theSearchBoxKeepsWhatWasTypedAndTheStatusKeepsTheSearch() throws Exception {
        String html = mockMvc.perform(get("/admin/orders")
                        .param("q", "Okafor")
                        .param("status", "PENDING_PAYMENT")
                        .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The box still shows the term, and the status links carry it, so
        // neither control silently clears the other.
        assertThat(html).contains("value=\"Okafor\"");
        assertThat(html).containsPattern("href=\"[^\"]*q=Okafor");
    }

    /** Nothing matching says so, and says what was actually searched. */
    @Test
    void anEmptyResultExplainsItself() throws Exception {
        String html = mockMvc.perform(get("/admin/orders")
                        .param("q", "nothing-matches-this-at-all")
                        .with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("No order matches");
    }
}
