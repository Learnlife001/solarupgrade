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
    private final EmailService emailService;
    private final Duration after;

    public OrderExpiryJob(OrderRepository orderRepository,
                          OrderService orderService,
                          EmailService emailService,
                          @Value("${app.order-expiry.after-hours:72}") long afterHours) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.emailService = emailService;
        this.after = Duration.ofHours(afterHours);
    }

    /**
     * Hourly. The delay before an order lapses is set by the cutoff, not by
     * how often this looks.
     */
    @Scheduled(fixedDelayString = "${app.order-expiry.interval-ms:3600000}",
            initialDelayString = "${app.order-expiry.initial-delay-ms:120000}")
    @Transactional
    public void expireStaleOrders() {
        Instant cutoff = Instant.now().minus(after);
        List<Order> stale = orderRepository
                .findByStatusAndPlacedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.info("Releasing stock from {} unpaid order(s) older than {}h", stale.size(), after.toHours());
        for (Order order : stale) {
            // The transition guard inside cancelUnpaid is what makes this safe
            // against an order that was paid between the query and here.
            if (orderService.cancelUnpaid(order)) {
                emailService.sendOrderExpired(order);
            }
        }
    }
}
