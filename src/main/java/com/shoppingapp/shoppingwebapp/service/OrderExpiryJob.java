package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.service.alerts.ErrorAlerter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Puts the stock from abandoned orders back on the shelf.
 *
 * <p>Placing an order takes its items out of stock immediately, which is right:
 * two people must not both be sold the last battery while one of them is on
 * PayPal's page. But nothing gave the stock back, so an order that was never
 * paid for held its items forever. A few abandoned baskets — or one person
 * placing orders they never intend to pay for — could empty the catalogue
 * without a naira changing hands.
 *
 * <p>The window is deliberately longer than the payment reminder's, so the
 * sequence a customer sees is: order, reminder, and only then expiry. Expiring
 * an order before its reminder has even gone out would be a nudge to pay for
 * something that no longer exists.
 *
 * <p>Enabled by default, unlike the reminder job: an order that quietly holds
 * stock for ever is a bug, not a feature to opt into. Set
 * {@code app.order-expiry.enabled=false} to turn it off.
 */
@Component
@ConditionalOnProperty(name = "app.order-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ErrorAlerter alerter;
    private final Duration after;

    public OrderExpiryJob(OrderRepository orderRepository,
                          OrderService orderService,
                          ErrorAlerter alerter,
                          @Value("${app.order-expiry.after-hours:72}") long afterHours) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.alerter = alerter;
        this.after = Duration.ofHours(afterHours);
    }

    /**
     * Hourly. The delay before an order lapses is set by the cutoff, not by
     * how often this looks.
     */
    /**
     * Deliberately <b>not</b> {@code @Transactional}.
     *
     * <p>It used to be, which put every order in the run inside one
     * transaction: a single order that threw took the whole run with it and
     * rolled back the ones already released. The orders most likely to throw
     * are the odd ones, so the failure mode was the shop's stock staying locked
     * up because of one strange row -- for ever, since the next run began at
     * the same order and failed the same way.
     *
     * <p>Each order is now its own transaction, inside {@code cancelUnpaid},
     * and one that fails is reported and stepped over.
     */
    @Scheduled(fixedDelayString = "${app.order-expiry.interval-ms:3600000}",
            initialDelayString = "${app.order-expiry.initial-delay-ms:120000}")
    public void expireStaleOrders() {
        Instant cutoff = Instant.now().minus(after);
        List<Order> stale = orderRepository
                .findByStatusAndPlacedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.info("Releasing stock from {} unpaid order(s) older than {}h", stale.size(), after.toHours());
        int failed = 0;
        for (Order order : stale) {
            try {
                // By id, and the service re-reads it: the guard inside
                // cancelUnpaid then runs against the row as it is now, not as
                // this query saw it, so an order paid in between is left alone.
                // The notification is sent from there too, so both routes to a
                // cancellation tell the customer the same thing.
                orderService.cancelUnpaid(order.getId());
            } catch (Exception ex) {
                failed++;
                log.error("Could not expire order {}", order.getId(), ex);
                alerter.jobFailed("Order expiry", "order #" + order.getId(), ex);
            }
        }
        if (failed > 0) {
            log.warn("Order expiry finished with {} of {} order(s) failing", failed, stale.size());
        }
    }
}
