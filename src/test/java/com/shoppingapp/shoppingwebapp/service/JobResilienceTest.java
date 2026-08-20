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
import com.shoppingapp.shoppingwebapp.service.alerts.ErrorAlerter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * One bad order must not take a whole scheduled run with it.
 *
 * <p>Both jobs used to be {@code @Transactional} across the entire loop, which
 * had two consequences nobody would choose. An order that threw halfway
 * through rolled back everything already done in that run — for the reminder
 * job that meant clearing the "already chased" flag on customers who had just
 * been emailed, so the next run emailed them again. And the run would fail the
 * same way every hour, because it started at the same order each time, while
 * unpaid orders held stock that was never released.
 *
 * <p>Not {@code @Transactional} itself, for the usual reason: these assert on
 * what the jobs committed.
 */
@SpringBootTest(properties = {
        "app.payment-reminders.enabled=true",
        // Long delays: the schedule must not fire on its own and race the test.
        "app.payment-reminders.initial-delay-ms=3600000",
        "app.order-expiry.initial-delay-ms=3600000"})
class JobResilienceTest {

    @Autowired
    private OrderExpiryJob expiryJob;

    @Autowired
    private PaymentReminderJob reminderJob;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CartService cartService;

    @Autowired
    private JdbcTemplate jdbc;

    /** Made to fail on one order and work on the rest. */
    @MockitoSpyBean
    private OrderService orderService;

    @MockitoBean
    private ErrorAlerter alerter;

    private Product product;
    private User buyer;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        buyer = users.save(new User("jobs-" + unique + "@example.test", "hash", "Job Buyer"));
        product = products.save(new Product("Job Panel " + unique, "A panel.",
                new BigDecimal("1000.00"), Category.PANEL, 100, null));
    }

    /** Old enough for the jobs to pick up, written straight to the column. */
    private Order stale(int daysAgo) {
        cartService.add(buyer, product, 1);
        CheckoutForm form = new CheckoutForm();
        form.setShippingName("Job Buyer");
        form.setShippingLine1("1 Test Street");
        form.setShippingCity("Lagos");
        form.setShippingState("Lagos");
        form.setShippingCountry("NG");
        form.setPaymentMethod(PaymentMethod.PAYPAL);
        Order order = orderService.placeOrder(buyer, form);

        jdbc.update("update orders set placed_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)), order.getId());
        return order;
    }

    /**
     * The one that matters: the run continues past a failure, so the other
     * orders still get their stock back.
     */
    @Test
    void oneFailingOrderDoesNotStopTheExpiryRun() {
        Order first = stale(10);
        Order poisoned = stale(10);
        Order last = stale(10);

        doCallRealMethod().when(orderService).cancelUnpaid(anyLong());
        doThrow(new IllegalStateException("this order is broken"))
                .when(orderService).cancelUnpaid(poisoned.getId());

        expiryJob.expireStaleOrders();

        // The broken one is untouched and the others are cancelled -- not all
        // three rolled back together, which is what used to happen.
        assertThat(status(poisoned)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(status(first)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(status(last)).isEqualTo(OrderStatus.CANCELLED);
    }

    /** And somebody is told, because no customer is there to notice. */
    @Test
    void aFailingOrderIsReported() {
        Order poisoned = stale(10);

        doCallRealMethod().when(orderService).cancelUnpaid(anyLong());
        doThrow(new IllegalStateException("this order is broken"))
                .when(orderService).cancelUnpaid(poisoned.getId());

        expiryJob.expireStaleOrders();

        verify(alerter).jobFailed(anyString(), anyString(), any(IllegalStateException.class));
    }

    /**
     * The reminder flag has to survive the run. Rolled back, it would clear on
     * customers who had already been emailed and chase them a second time.
     */
    @Test
    void remindersAlreadySentSurviveALaterFailure() {
        stale(2);
        stale(2);

        reminderJob.sendDueReminders();

        List<Order> all = orders.findByUserOrderByPlacedAtDesc(buyer);
        assertThat(all).isNotEmpty();
        assertThat(all).allSatisfy(order ->
                assertThat(order.getPaymentReminderSentAt()).isNotNull());
    }

    private OrderStatus status(Order order) {
        return orders.findById(order.getId()).orElseThrow().getStatus();
    }
}
