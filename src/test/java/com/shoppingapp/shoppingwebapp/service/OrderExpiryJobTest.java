package com.shoppingapp.shoppingwebapp.service;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderExpiryJobTest {

    @Autowired
    private OrderExpiryJob job;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User user;
    private Product panel;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("expiry-test@example.test", "hash", "Expiry Tester"));
        panel = productRepository.save(
                new Product("Expiry Panel", "desc", new BigDecimal("250000.00"), Category.PANEL, 10, null));
    }

    private Order unpaidOrder(int quantity) {
        cartService.add(user, panel, quantity);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Expiry Tester");
        form.setShippingLine1("14 Adeola Odeku Street");
        form.setShippingCity("Victoria Island");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        return orderService.placeOrder(user, form);
    }

    /** placedAt is set on construction, so an old order has to be aged by hand. */
    private void backdate(Order order, int hours) {
        entityManager.createQuery("update Order o set o.placedAt = :when where o.id = :id")
                .setParameter("when", Instant.now().minus(hours, ChronoUnit.HOURS))
                .setParameter("id", order.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private int stock() {
        return productRepository.findById(panel.getId()).orElseThrow().getStock();
    }

    @Test
    void anAbandonedOrderGivesItsStockBack() {
        Order order = unpaidOrder(3);
        assertThat(stock()).isEqualTo(7);
        backdate(order, 96);

        job.expireStaleOrders();

        assertThat(stock()).isEqualTo(10);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    /** The whole point of the delay: a customer still deciding keeps their order. */
    @Test
    void aRecentUnpaidOrderIsLeftAlone() {
        Order order = unpaidOrder(3);
        backdate(order, 2);

        job.expireStaleOrders();

        assertThat(stock()).isEqualTo(7);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    /**
     * The dangerous case. Cancelling a paid order would hand back stock for
     * goods that are owed to someone who has already paid for them.
     */
    @Test
    void aPaidOrderIsNeverExpired() {
        Order order = unpaidOrder(3);
        orderService.markPaid(order);
        backdate(order, 500);

        job.expireStaleOrders();

        assertThat(stock()).isEqualTo(7);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    /**
     * Two runs must not return the same units twice — otherwise a job that
     * retries would invent stock out of nothing.
     */
    @Test
    void runningTwiceReturnsTheStockOnlyOnce() {
        Order order = unpaidOrder(4);
        backdate(order, 96);

        job.expireStaleOrders();
        job.expireStaleOrders();

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void cancellingAnAlreadyCancelledOrderReportsThatItDidNothing() {
        Order order = unpaidOrder(1);

        assertThat(orderService.cancelUnpaid(order.getId())).isTrue();
        assertThat(orderService.cancelUnpaid(order.getId())).isFalse();
    }
}
