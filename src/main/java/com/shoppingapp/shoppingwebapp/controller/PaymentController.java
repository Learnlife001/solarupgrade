package com.shoppingapp.shoppingwebapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentException;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The provider-facing half of checkout: where the buyer is sent, where they
 * come back to, and where the provider reports what happened.
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    /** Only these five are read; PayPal signs the whole set. */
    private static final String[] SIGNATURE_HEADERS = {
            "paypal-auth-algo", "paypal-cert-url", "paypal-transmission-id",
            "paypal-transmission-sig", "paypal-transmission-time"};

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final CurrentUserSupport currentUser;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService,
                             OrderService orderService,
                             CurrentUserSupport currentUser,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    /**
     * Where PayPal sends the buyer after they approve.
     *
     * <p>Arriving here is not proof of anything -- the URL could be typed. It
     * is the capture call this triggers, which we make to PayPal ourselves,
     * whose answer decides whether the order is paid.
     *
     * <p>Scoped to the signed-in user, so pasting someone else's order number
     * captures nothing.
     */
    @GetMapping("/paypal/return")
    public String payPalReturn(@RequestParam("order") Long orderId,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        User user = currentUser.require(principal);
        Order order = orderService.getForUser(orderId, user);

        try {
            if (paymentService.completePayPal(order, user)) {
                redirectAttributes.addFlashAttribute("message", "Payment received — thank you.");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "PayPal did not complete the payment. Nothing has been charged.");
            }
        } catch (PaymentException ex) {
            log.warn("PayPal capture failed for order {}", orderId, ex);
            redirectAttributes.addFlashAttribute("error",
                    "We could not confirm that payment. Nothing has been charged — please try again.");
        }
        return "redirect:/orders/" + orderId;
    }

    /** The buyer backed out on PayPal's side. The order stays unpaid. */
    @GetMapping("/paypal/cancel")
    public String payPalCancel(@RequestParam("order") Long orderId,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        currentUser.require(principal);
        redirectAttributes.addFlashAttribute("message",
                "Payment cancelled. Your order is still here when you want it.");
        return "redirect:/orders/" + orderId;
    }

    /**
     * PayPal's server-to-server notification.
     *
     * <p>Open to the internet by necessity, so every body is treated as hostile
     * until PayPal itself confirms it signed it. Unverified means ignored --
     * never "probably fine".
     *
     * <p>Answers 200 to anything it decides not to act on, because a provider
     * that gets an error retries for hours; the log line is how a genuine
     * problem gets noticed.
     */
    @PostMapping("/paypal/webhook")
    @ResponseBody
    public ResponseEntity<String> payPalWebhook(@RequestBody String rawBody,
                                                HttpServletRequest request) {
        if (!paymentService.webhookVerificationConfigured()) {
            // No webhook id means no way to tell a real notification from a
            // forged one, so nothing is acted on.
            log.warn("Received a PayPal webhook but app.paypal.webhook-id is not set; ignoring");
            return ResponseEntity.ok("ignored");
        }
        if (!paymentService.verifyWebhook(signatureHeaders(request), rawBody)) {
            log.warn("Rejected a PayPal webhook that failed signature verification");
            return ResponseEntity.ok("rejected");
        }

        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String type = event.path("event_type").asText("");
            if (!"PAYMENT.CAPTURE.COMPLETED".equals(type)) {
                return ResponseEntity.ok("ignored");
            }

            JsonNode resource = event.path("resource");
            Long orderId = parseOrderId(resource.path("custom_id").asText(null));
            if (orderId == null) {
                log.warn("PayPal webhook carried no usable custom_id");
                return ResponseEntity.ok("ignored");
            }

            paymentService.settleFromWebhook(
                    orderId,
                    resource.path("supplementary_data").path("related_ids")
                            .path("order_id").asText(null),
                    amountOf(resource),
                    resource.path("amount").path("currency_code").asText(null));
        } catch (Exception ex) {
            log.error("Could not process a verified PayPal webhook", ex);
        }
        return ResponseEntity.ok("ok");
    }

    private static Map<String, String> signatureHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String name : SIGNATURE_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return Collections.unmodifiableMap(headers);
    }

    private static Long parseOrderId(String customId) {
        try {
            return customId == null ? null : Long.valueOf(customId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal amountOf(JsonNode resource) {
        String value = resource.path("amount").path("value").asText(null);
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
