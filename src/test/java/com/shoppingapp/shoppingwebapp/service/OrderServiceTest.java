package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    private User user;
    private Product panel;

    private static CheckoutForm checkoutForm() {
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Order Tester");
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingCity("Victoria Island");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.CARD);
        return form;
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("order-test@example.com", "hash", "Order Tester"));
        panel = productRepository.save(
                new Product("Test Panel", "desc", new BigDecimal("189.00"), Category.PANEL, 10, null));
    }

    @Test
    void placingAnOrderCopiesTheBasketAndEmptiesIt() {
        cartService.add(user, panel, 2);

        Order order = orderService.placeOrder(user, checkoutForm());

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotal()).isEqualByComparingTo("378.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(cartService.itemsFor(user)).isEmpty();
    }

    @Test
    void placingAnOrderDecrementsStock() {
        cartService.add(user, panel, 3);

        orderService.placeOrder(user, checkoutForm());

        assertThat(productRepository.findById(panel.getId()).orElseThrow().getStock()).isEqualTo(7);
    }

    @Test
    void placingAnOrderWithAnEmptyBasketIsRejected() {
        assertThatThrownBy(() -> orderService.placeOrder(user, checkoutForm()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void orderingMoreThanIsInStockIsRejected() {
        cartService.add(user, panel, 11);

        assertThatThrownBy(() -> orderService.placeOrder(user, checkoutForm()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not enough stock");
    }

    @Test
    void anOrderCannotBeReadByAnotherUser() {
        cartService.add(user, panel, 1);
        Order order = orderService.placeOrder(user, checkoutForm());
        User intruder = userRepository.save(new User("intruder2@example.com", "hash", "Intruder"));

        assertThatThrownBy(() -> orderService.getForUser(order.getId(), intruder))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void theChosenPaymentMethodIsRecordedOnTheOrder() {
        cartService.add(user, panel, 1);
        CheckoutForm form = checkoutForm();
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

        Order order = orderService.placeOrder(user, form);

        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void theAddressIsStoredAsFieldsAndRenderedAsLines() {
        cartService.add(user, panel, 1);
        CheckoutForm form = checkoutForm();
        form.setShippingLine2("Flat 3B");

        Order order = orderService.placeOrder(user, form);

        assertThat(order.getShippingLine1()).isEqualTo("14 Adeola Odeku Street");
        assertThat(order.getShippingCity()).isEqualTo("Victoria Island");
        assertThat(order.getShippingState()).isEqualTo("Lagos");
        assertThat(order.getShippingLines())
                .containsExactly("14 Adeola Odeku Street", "Flat 3B", "Victoria Island", "Lagos", "Nigeria");
    }

    @Test
    void anAddressWithNoPostcodeLosesTheLineRatherThanShowingABlankOne() {
        cartService.add(user, panel, 1);

        // Most Nigerian addresses have no postcode; the label must not end up
        // with an empty line or a stray separator where one would have been.
        Order order = orderService.placeOrder(user, checkoutForm());

        // Null, not "": a missing postcode is an absent value, not an empty one.
        assertThat(order.getShippingPostcode()).isNull();
        assertThat(order.getShippingLines()).noneMatch(String::isBlank);
        assertThat(order.getShippingLines()).contains("Victoria Island");
    }

    @Test
    void aPostcodeSharesTheCityLineTheWayAnAddressLabelReads() {
        cartService.add(user, panel, 1);
        CheckoutForm form = checkoutForm();
        form.setShippingCity("Leeds");
        form.setShippingPostcode("LS6 3QB");
        form.setShippingCountry("GB");

        Order order = orderService.placeOrder(user, form);

        assertThat(order.getShippingLines()).contains("Leeds LS6 3QB");
    }

    @Test
    void withdrawnMethodsAreNotOfferedButStillLoadOnOldOrders() {
        assertThat(PaymentMethod.offered())
                .containsExactly(PaymentMethod.CARD, PaymentMethod.PAYPAL, PaymentMethod.BANK_TRANSFER);

        // Kept as constants on purpose: an order placed with one of these must
        // still render rather than throwing on an unknown enum name.
        assertThat(PaymentMethod.valueOf("KLARNA").isOffered()).isFalse();
        assertThat(PaymentMethod.valueOf("APPLE_PAY").isOffered()).isFalse();
        assertThat(PaymentMethod.valueOf("SEPA").isOffered()).isFalse();
    }

    @Test
    void markPaidMovesTheOrderOutOfPendingPayment() {
        cartService.add(user, panel, 1);
        Order order = orderService.placeOrder(user, checkoutForm());

        Order paid = orderService.markPaid(order.getId(), user);

        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
