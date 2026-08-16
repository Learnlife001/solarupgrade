package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Nudges customers who placed an order and never finished paying.
 *
 * <p>Exactly one reminder per order, recorded on the row itself. An unpaid
 * order that emails someone every hour is worse than one that never emails at
 * all, and "did we already chase this?" has to survive a restart, so the fact
 * lives in the database rather than in memory.
 *
 * <p>Off unless {@code app.payment-reminders.enabled} is set. On a single
 * instance that is all the guarding needed; running more than one copy of the
 * app would need a lock here, because two schedulers would otherwise both pick
 * up the same order in the window before either commits.
 */
@Component
@ConditionalOnProperty(name = "app.payment-reminders.enabled", havingValue = "true")
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);

    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final Duration after;

    public PaymentReminderJob(OrderRepository orderRepository,
                              EmailService emailService,
                              @Value("${app.payment-reminders.after-hours:24}") long afterHours) {
        this.orderRepository = orderRepository;
        this.emailService = emailService;
        this.after = Duration.ofHours(afterHours);
    }

    /**
     * Hourly, which is far finer than the reminder delay needs -- the delay is
     * decided by the cutoff below, not by how often this runs.
     */
    @Scheduled(fixedDelayString = "${app.payment-reminders.interval-ms:3600000}",
            initialDelayString = "${app.payment-reminders.initial-delay-ms:60000}")
    @Transactional
    public void sendDueReminders() {
        Instant cutoff = Instant.now().minus(after);
        List<Order> due = orderRepository
                .findByStatusAndPaymentReminderSentAtIsNullAndPlacedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        if (due.isEmpty()) {
            return;
        }

        log.info("Sending payment reminders for {} unpaid order(s)", due.size());
        for (Order order : due) {
            // Marked before sending, and inside the transaction: if the send
            // throws, the order is still flagged and the customer gets one
            // email at most. EmailService swallows failures anyway, so a
            // provider outage costs the reminder rather than repeating it.
            order.markPaymentReminderSent();
            orderRepository.save(order);
            emailService.sendPaymentReminder(order);
        }
    }
}
