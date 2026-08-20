package com.shoppingapp.shoppingwebapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.service.OrderService;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentException;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentProvider;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * Where a provider sends the buyer after they approve.
     *
     * <p>Arriving here is not proof of anything -- the URL could be typed. It
     * is the capture call this triggers, which we make to the provider
     * ourselves, whose answer decides whether the order is paid.
     *
     * <p>Scoped to the signed-in user, so pasting someone else's order number
     * captures nothing.
     *
     * <p>The provider is a path variable rather than a route each: PayPal's
     * existing /payments/paypal/return keeps working unchanged, and a provider
     * added later needs no new controller.
     */
    @GetMapping("/{provider}/return")
    public String paymentReturn(@PathVariable("provider") String providerId,
                                @RequestParam("order") Long orderId,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser.require(principal);
        Order order = orderService.getForUser(orderId, user);

        try {
            if (paymentService.complete(order)) {
                redirectAttributes.addFlashAttribute("message", "Payment received — thank you.");
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "The payment was not completed. Nothing has been charged.");
            }
        } catch (PaymentException ex) {
            log.warn("Capture failed for order {} at {}", orderId, providerId, ex);
            redirectAttributes.addFlashAttribute("error",
                    "We could not confirm that payment. Nothing has been charged — please try again.");
        }
        return "redirect:/orders/" + orderId;
    }

    /** The buyer backed out on the provider's side. The order stays unpaid. */
    @GetMapping("/{provider}/cancel")
    public String paymentCancel(@PathVariable("provider") String providerId,
                                @RequestParam("order") Long orderId,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        currentUser.require(principal);
        redirectAttributes.addFlashAttribute("message",
                "Payment cancelled. Your order is still here when you want it.");
        return "redirect:/orders/" + orderId;
    }

    /**
     * A provider's server-to-server notification.
     *
     * <p>Open to the internet by necessity, so every body is treated as hostile
     * until the provider itself confirms it signed it. Unverified means ignored
     * -- never "probably fine".
     *
     * <p>Answers 200 to anything it decides not to act on, because a provider
     * that gets an error retries for hours; the log line is how a genuine
     * problem gets noticed.
     *
     * <p>Nothing here knows one provider's JSON from another's. The provider
     * verifies the signature and reads its own body; this method decides only
     * whether to act on what comes back.
     */
    @PostMapping("/{provider}/webhook")
    @ResponseBody
    public ResponseEntity<String> webhook(@PathVariable("provider") String providerId,
                                          @RequestBody String rawBody,
                                          HttpServletRequest request) {
        PaymentProvider provider = paymentService.byId(providerId).orElse(null);
        if (provider == null) {
            log.warn("Webhook for unknown payment provider {}", providerId);
            return ResponseEntity.ok("ignored");
        }
        if (!provider.canVerifyWebhooks()) {
            // No signing secret means no way to tell a real notification from a
            // forged one, so nothing is acted on.
            log.warn("Received a {} webhook but it cannot be verified on this deployment; ignoring",
                    providerId);
            return ResponseEntity.ok("ignored");
        }
        if (!provider.verifyWebhook(signatureHeaders(request, provider), rawBody)) {
            log.warn("Rejected a {} webhook that failed signature verification", providerId);
            return ResponseEntity.ok("rejected");
        }

        provider.readWebhook(rawBody).ifPresent(event -> paymentService.settleFromWebhook(
                event.orderId(), event.reference(), event.amount(), event.currency()));
        return ResponseEntity.ok("ok");
    }

    private static Map<String, String> signatureHeaders(HttpServletRequest request,
                                                        PaymentProvider provider) {
        Map<String, String> headers = new HashMap<>();
        for (String name : provider.signatureHeaders()) {
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
