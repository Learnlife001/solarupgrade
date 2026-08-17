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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rule these tests exist to hold: <b>pressing a button on this site cannot
 * make an order paid.</b>
 *
 * <p>It could, until this was written. The order page offered a pay button for
 * every method, and where no provider was configured the button called
 * markPaid directly. Card and bank transfer had no provider, so any buyer who
 * chose one could settle their own seven-figure order by pressing it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentAvailabilityTest {

    private static final String EMAIL = "payment-availability@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = userRepository.findByEmail(EMAIL)
                .orElseGet(() -> userRepository.save(new User(EMAIL, "hash", "Payment Tester")));
        product = productRepository.save(
                new Product("Availability Panel", "desc", new BigDecimal("250000.00"), Category.PANEL, 5, null));
    }

    private Order orderPaidBy(PaymentMethod method) {
        cartService.add(user, product, 1);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Payment Tester");
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingCity("Victoria Island");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(method);
        return orderService.placeOrder(user, form);
    }

    /**
     * The exact attack the old code allowed, run against the current code.
     * Placed through the service so that closing the checkout door does not
     * hide whether the payment door is still open.
     */
    @Test
    void pressingPayOnAMethodWithNoProviderDoesNotMarkTheOrderPaid() throws Exception {
        Order order = orderPaidBy(PaymentMethod.BANK_TRANSFER);

        mockMvc.perform(post("/orders/" + order.getId() + "/pay")
                        .with(user(EMAIL))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + order.getId()));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /** Same for card, which had the same hole. */
    @Test
    void theSameIsTrueOfCard() throws Exception {
        Order order = orderPaidBy(PaymentMethod.CARD);

        mockMvc.perform(post("/orders/" + order.getId() + "/pay")
                        .with(user(EMAIL))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /**
     * The checkout page never shows these methods, but a form post is not the
     * page: the method has to be checked on arrival, not merely left off the
     * list of radios.
     */
    @Test
    void checkoutRefusesAMethodThatIsNotOfferedEvenIfPostedDirectly() throws Exception {
        cartService.add(user, product, 1);

        mockMvc.perform(post("/checkout")
                        .param("shippingName", "Payment Tester")
                        .param("shippingLine1", "14 Adeola Odeku Street")
                        .param("shippingCity", "Victoria Island")
                        .param("shippingState", "Lagos")
                        .param("shippingCountry", "NG")
                        .param("paymentMethod", "BANK_TRANSFER")
                        .with(user(EMAIL))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("checkoutForm", "paymentMethod"));

        // No order at all, rather than one that cannot be paid for.
        assertThat(orderRepository.findAll()
                .stream()
                .noneMatch(o -> o.getUser().getId().equals(user.getId())))
                .isTrue();
    }

    /**
     * The order page has a branch that only an order with an unavailable
     * method reaches. Nothing renders it in normal use any more, which is
     * exactly why it needs a test: a broken expression there would surface
     * first to a customer holding an order they cannot pay for.
     */
    @Test
    void anOrderOnAnUnavailableMethodStillRenders() throws Exception {
        Order order = orderPaidBy(PaymentMethod.BANK_TRANSFER);

        mockMvc.perform(get("/orders/" + order.getId()).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be paid here yet")))
                .andExpect(content().string(not(containsString("Continue to PayPal"))));
    }

    /** The basket survives a rejected checkout, so nothing has to be rebuilt. */
    @Test
    void aRejectedCheckoutLeavesTheBasketAlone() throws Exception {
        cartService.add(user, product, 2);

        mockMvc.perform(post("/checkout")
                        .param("shippingName", "Payment Tester")
                        .param("shippingLine1", "14 Adeola Odeku Street")
                        .param("shippingCity", "Victoria Island")
                        .param("shippingState", "Lagos")
                        .param("shippingCountry", "NG")
                        .param("paymentMethod", "CARD")
                        .with(user(EMAIL))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(cartService.itemsFor(user)).hasSize(1);
    }
}
