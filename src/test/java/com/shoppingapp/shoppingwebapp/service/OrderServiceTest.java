package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.Category;
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
        form.setShippingAddress("1 Test Street");
        form.setShippingPostcode("AB1 2CD");
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
    void markPaidMovesTheOrderOutOfPendingPayment() {
        cartService.add(user, panel, 1);
        Order order = orderService.placeOrder(user, checkoutForm());

        Order paid = orderService.markPaid(order.getId(), user);

        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
