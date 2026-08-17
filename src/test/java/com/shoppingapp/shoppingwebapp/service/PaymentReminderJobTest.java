package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.CheckoutForm;
import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Order;
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

/**
 * A long initial delay keeps the scheduler from firing during the test, so
 * every run here is a direct call: the assertions are about what the job does,
 * not about when it happens to wake up.
 */
@SpringBootTest(properties = {
        "app.payment-reminders.enabled=true",
        "app.payment-reminders.after-hours=24",
        "app.payment-reminders.initial-delay-ms=600000"
})
@Transactional
class PaymentReminderJobTest {

    @Autowired
    private PaymentReminderJob job;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User user;
    private Product panel;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("reminder@example.test", "hash", "Reminder Tester"));
        panel = productRepository.save(
                new Product("Reminder Panel", "desc", new BigDecimal("100.00"), Category.PANEL, 50, null));
    }

    private Order unpaidOrder() {
        cartService.add(user, panel, 1);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Reminder Tester");
        form.setShippingLine1("1 Test Close");
        form.setShippingCity("Ikeja");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        return orderService.placeOrder(user, form);
    }

    /**
     * Ages an order past the cutoff rather than waiting a day for it. Done in
     * the test rather than through a setter, because placedAt is deliberately
     * not settable on the entity.
     */
    private void backdate(Order order, long hours) {
        entityManager.createQuery("update Order o set o.placedAt = :when where o.id = :id")
                .setParameter("when", Instant.now().minus(hours, ChronoUnit.HOURS))
                .setParameter("id", order.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private Order reload(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @Test
    void aFreshUnpaidOrderIsNotChasedYet() {
        Order order = unpaidOrder();

        job.sendDueReminders();

        assertThat(reload(order.getId()).getPaymentReminderSentAt()).isNull();
    }

    @Test
    void anOldUnpaidOrderIsChasedExactlyOnce() {
        Order order = unpaidOrder();
        backdate(order, 48);

        job.sendDueReminders();
        Instant firstRun = reload(order.getId()).getPaymentReminderSentAt();
        assertThat(firstRun).isNotNull();

        // The whole point of storing the timestamp: a second pass must leave
        // it alone rather than mail the customer again.
        job.sendDueReminders();
        assertThat(reload(order.getId()).getPaymentReminderSentAt()).isEqualTo(firstRun);
    }

    @Test
    void aPaidOrderIsNeverChased() {
        Order order = unpaidOrder();
        backdate(order, 48);
        orderService.markPaid(order);

        job.sendDueReminders();

        assertThat(reload(order.getId()).getPaymentReminderSentAt()).isNull();
    }
}
