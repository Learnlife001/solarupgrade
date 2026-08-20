package com.shoppingapp.shoppingwebapp.controller;

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
import com.shoppingapp.shoppingwebapp.service.ResendMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer changing their mind.
 *
 * <p>Until this existed the only thing they could do with an order was pay for
 * it, so anybody who decided against one waited three days for the expiry job
 * or wrote in — with their items off the shelf the whole time.
 *
 * <p>Not {@code @Transactional}: these post and then read what was committed.
 */
@SpringBootTest(properties = "app.mail.resend.api-key=test-key")
@AutoConfigureMockMvc
class CustomerCancelTest {

    @Autowired
    private MockMvc mockMvc;

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

    @MockitoBean
    private ResendMailer resendMailer;

    private String buyerEmail;
    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        buyerEmail = "cancel-buyer-" + unique + "@example.test";
        User buyer = save(buyerEmail);
        product = products.save(new Product("Cancel Panel " + unique, "A panel.",
                new BigDecimal("1000.00"), Category.PANEL, 10, null));

        cartService.add(buyer, product, 3);
        order = place(buyer);
    }

    private User save(String email) {
        return users.findByEmail(email).orElseGet(() -> {
            User created = new User(email, "hash", "Test Buyer");
            created.markEmailVerified();
            return users.save(created);
        });
    }

    private Order place(User buyer) {
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Test Buyer");
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return orderService.placeOrder(buyer, form);
    }

    private OrderStatus orderStatus() {
        return orders.findById(order.getId()).orElseThrow().getStatus();
    }

    private int stock() {
        return products.findById(product.getId()).orElseThrow().getStock();
    }

    @Test
    void aCustomerCanCancelTheirOwnUnpaidOrder() throws Exception {
        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                        .with(user(buyerEmail)).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + order.getId()));

        assertThat(orderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    /** The point of doing it sooner: the items go back on the shelf. */
    @Test
    void theStockGoesBack() throws Exception {
        assertThat(stock()).isEqualTo(7);

        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                .with(user(buyerEmail)).with(csrf()));

        assertThat(stock()).isEqualTo(10);
    }

    /**
     * Somebody else's order number is not a cancel button. The order is loaded
     * for the signed-in customer, so a pasted id belongs to nobody.
     */
    @Test
    void aCustomerCannotCancelSomebodyElsesOrder() throws Exception {
        String stranger = "stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        save(stranger);

        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                        .with(user(stranger)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /**
     * Money that has moved needs a refund, which is a decision rather than a
     * button. The service refuses whatever the page offered.
     */
    @Test
    void aPaidOrderCannotBeCancelledThisWay() throws Exception {
        orderService.markPaid(orders.findById(order.getId()).orElseThrow());

        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                .with(user(buyerEmail)).with(csrf()));

        assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
    }

    /** Twice is once: the second attempt cannot return the stock again. */
    @Test
    void cancellingTwiceDoesNotReturnTheStockTwice() throws Exception {
        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                .with(user(buyerEmail)).with(csrf()));
        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                .with(user(buyerEmail)).with(csrf()));

        assertThat(stock()).isEqualTo(10);
    }

    /**
     * The confirmation must not read like the expiry notice. "This order was
     * never paid for, so we have released it", sent to somebody who pressed
     * Cancel a moment ago, reads as though nobody was listening.
     */
    @Test
    void theEmailConfirmsWhatTheyDidRatherThanAccusingThemOfNotPaying() throws Exception {
        mockMvc.perform(post("/orders/" + order.getId() + "/cancel")
                .with(user(buyerEmail)).with(csrf()));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(resendMailer, atLeastOnce())
                .send(anyString(), anyString(), anyString(), anyString(), html.capture());

        assertThat(html.getValue())
                .contains("cancelled as you asked")
                .doesNotContain("has lapsed")
                .doesNotContain("never paid for");
    }

    /** The button is only there while there is something to cancel. */
    @Test
    void theButtonIsOnlyOfferedWhileTheOrderIsUnpaid() throws Exception {
        String unpaid = mockMvc.perform(get("/orders/" + order.getId()).with(user(buyerEmail)))
                .andReturn().getResponse().getContentAsString();
        assertThat(unpaid).contains("Cancel this order");

        orderService.markPaid(orders.findById(order.getId()).orElseThrow());

        String paid = mockMvc.perform(get("/orders/" + order.getId()).with(user(buyerEmail)))
                .andReturn().getResponse().getContentAsString();
        assertThat(paid).doesNotContain("Cancel this order");
    }
}
