package com.shoppingapp.shoppingwebapp.service.payment;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.OrderRepository;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Starts and finishes payments.
 *
 * <p>The one rule everything here is built around: <b>an order becomes PAID
 * only because a provider told us, in an exchange we initiated, that money
 * moved.</b> A buyer's browser arriving at a return URL is not that -- a URL
 * can be typed, bookmarked, shared or replayed. What the return URL does is
 * trigger a capture call; the capture's <em>response</em> is the evidence.
 * The webhook is the same evidence arriving by another route, for when the
 * buyer closes the tab before coming back.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final ObjectProvider<PayPalClient> payPalProvider;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final String baseUrl;

    public PaymentService(ObjectProvider<PayPalClient> payPalProvider,
                          OrderRepository orderRepository,
                          OrderService orderService,
                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.payPalProvider = payPalProvider;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** True when this method can actually take money right now. */
    public boolean isLive(PaymentMethod method) {
        return method == PaymentMethod.PAYPAL && payPalProvider.getIfAvailable() != null;
    }

    /**
     * Creates the payment at the provider and returns where to send the buyer.
     *
     * <p>The amount comes from the order's own snapshot, never from a fresh
     * conversion: the buyer pays the figure they were quoted.
     */
    @Transactional
    public String beginPayPal(Order order) {
        PayPalClient payPal = requirePayPal();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentException("Order " + order.getId() + " is not awaiting payment");
        }

        PayPalClient.CreatedOrder created = payPal.createOrder(
                order.getId(),
                order.getPaymentAmount(),
                order.getPaymentCurrency(),
                baseUrl + "/payments/paypal/return?order=" + order.getId(),
                baseUrl + "/payments/paypal/cancel?order=" + order.getId());

        order.setProviderReference(created.id());
        orderRepository.save(order);
        log.info("Started PayPal order {} for order {}", created.id(), order.getId());
        return created.approvalUrl();
    }

    /**
     * Captures the approved payment and marks the order paid if it completed.
     *
     * @return true when the order is paid as a result (or already was)
     */
    @Transactional
    public boolean completePayPal(Order order, User user) {
        if (order.getStatus() == OrderStatus.PAID) {
            // Already settled, most likely by the webhook getting here first.
            return true;
        }
        String reference = order.getProviderReference();
        if (reference == null || reference.isBlank()) {
            throw new PaymentException("Order " + order.getId() + " has no PayPal order to capture");
        }

        PayPalClient.Capture capture = requirePayPal().capture(reference);
        if (!capture.completed()) {
            log.warn("PayPal capture for order {} came back {}", order.getId(), capture.status());
            return false;
        }
        if (!chargeMatches(order, capture)) {
            // Refuse to dispatch goods against a payment that is not the one
            // we asked for. Louder than a silent pass, and rarer than a bug.
            log.error("PayPal captured {} {} for order {} but we asked for {} {}",
                    capture.amount(), capture.currency(), order.getId(),
                    order.getPaymentAmount(), order.getPaymentCurrency());
            throw new PaymentException("Captured amount does not match order " + order.getId());
        }

        orderService.markPaid(order.getId(), user);
        log.info("Order {} paid via PayPal {}", order.getId(), reference);
        return true;
    }

    /**
     * Settles an order from a webhook that has already been proved genuine.
     *
     * <p>Idempotent: a replayed notification finds the order paid and does
     * nothing, which matters because providers retry.
     */
    @Transactional
    public void settleFromWebhook(Long orderId, String reference, BigDecimal amount, String currency) {
        Optional<Order> found = orderRepository.findById(orderId);
        if (found.isEmpty()) {
            log.warn("Webhook referenced unknown order {}", orderId);
            return;
        }
        Order order = found.get();

        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        // The reference must be the one we started, so a genuine notification
        // for someone else's payment cannot settle this order.
        if (reference != null && order.getProviderReference() != null
                && !reference.equals(order.getProviderReference())) {
            log.warn("Webhook reference {} does not match order {}", reference, orderId);
            return;
        }
        if (amount != null && currency != null
                && !(currency.equals(order.getPaymentCurrency())
                     && amount.compareTo(order.getPaymentAmount()) == 0)) {
            log.error("Webhook reported {} {} for order {} but we asked for {} {}",
                    amount, currency, orderId, order.getPaymentAmount(), order.getPaymentCurrency());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        log.info("Order {} paid via webhook", orderId);
    }

    public boolean verifyWebhook(java.util.Map<String, String> headers, String rawBody) {
        PayPalClient payPal = payPalProvider.getIfAvailable();
        return payPal != null && payPal.verifyWebhook(headers, rawBody);
    }

    public boolean webhookVerificationConfigured() {
        PayPalClient payPal = payPalProvider.getIfAvailable();
        return payPal != null && payPal.canVerifyWebhooks();
    }

    private static boolean chargeMatches(Order order, PayPalClient.Capture capture) {
        if (capture.amount() == null || capture.currency() == null) {
            return false;
        }
        return capture.currency().equals(order.getPaymentCurrency())
                && capture.amount().compareTo(order.getPaymentAmount()) == 0;
    }

    private PayPalClient requirePayPal() {
        PayPalClient payPal = payPalProvider.getIfAvailable();
        if (payPal == null) {
            throw new PaymentException("PayPal is not configured on this deployment");
        }
        return payPal;
    }
}
