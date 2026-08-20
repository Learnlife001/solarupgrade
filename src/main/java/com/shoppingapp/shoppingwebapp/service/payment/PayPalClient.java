package com.shoppingapp.shoppingwebapp.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * PayPal Orders API v2, server side.
 *
 * <p>Registered only when a client id is configured, so the app still starts
 * with no PayPal set up at all -- {@link PaymentService} reports the method as
 * not live and the order page falls back to its stand-in.
 *
 * <p>Nothing here ever sees a card number. PayPal's own approval page collects
 * whatever the buyer pays with; this code only creates the order, sends the
 * buyer there, and asks afterwards whether the money moved.
 */
@Component
@ConditionalOnProperty(name = "app.paypal.client-id")
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);

    /** Refresh a little early rather than discover expiry mid-checkout. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String webhookId;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public PayPalClient(RestClient.Builder builder,
                        @Value("${app.paypal.client-id}") String clientId,
                        @Value("${app.paypal.client-secret}") String clientSecret,
                        @Value("${app.paypal.webhook-id:}") String webhookId,
                        @Value("${app.paypal.env:sandbox}") String env) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.webhookId = webhookId;
        this.client = builder.baseUrl(baseUrlFor(env)).build();
        log.info("PayPal client configured against {}", baseUrlFor(env));
    }

    /**
     * Refuses anything that is not exactly sandbox or live.
     *
     * <p>Treating unrecognised values as sandbox would be friendlier and much
     * worse: a production deployment misconfigured by one typo would take real
     * customers to the test PayPal, where no money moves and no order ever
     * settles, and nothing would say so. Failing at startup is loud, immediate,
     * and happens before a single customer sees the site.
     */
    static String baseUrlFor(String env) {
        String value = env == null ? "" : env.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "live", "production" -> "https://api-m.paypal.com";
            case "sandbox" -> "https://api-m.sandbox.paypal.com";
            default -> throw new IllegalArgumentException(
                    "app.paypal.env must be 'sandbox' or 'live', not '" + env + "'");
        };
    }

    public boolean canVerifyWebhooks() {
        return webhookId != null && !webhookId.isBlank();
    }

    /**
     * Client-credentials token, cached until shortly before it expires.
     *
     * <p>Fetching one per API call would work and would also double the
     * round trips on every checkout.
     */
    private String accessToken() {
        String token = cachedToken;
        if (token != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        TokenResponse response;
        try {
            response = client.post()
                    .uri("/v1/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException ex) {
            // Wrapped so callers have one exception type meaning "we could not
            // talk to the provider", rather than having to know that a DNS
            // failure and a 401 arrive as different classes.
            throw new PaymentException("Could not authenticate with PayPal", ex);
        }

        if (response == null || response.access_token() == null) {
            throw new PaymentException("PayPal returned no access token");
        }
        cachedToken = response.access_token();
        cachedTokenExpiry = Instant.now()
                .plusSeconds(Math.max(0, response.expires_in()))
                .minus(EXPIRY_MARGIN);
        return cachedToken;
    }

    /**
     * Creates the order and returns its id with the page to send the buyer to.
     *
     * @param amount   the figure snapshotted on our order, never recomputed here
     * @param currency the currency that figure is in
     */
    public CreatedOrder createOrder(Long ourOrderId,
                                    BigDecimal amount,
                                    String currency,
                                    String returnUrl,
                                    String cancelUrl) {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        // Ours, echoed back on the webhook, so a notification
                        // can be tied to an order without trusting the URL.
                        "custom_id", String.valueOf(ourOrderId),
                        "invoice_id", "SOLARUPGRADE-" + ourOrderId,
                        "amount", Map.of(
                                "currency_code", currency,
                                "value", amount.toPlainString()))),
                "payment_source", Map.of("paypal", Map.of(
                        "experience_context", Map.of(
                                "return_url", returnUrl,
                                "cancel_url", cancelUrl,
                                "user_action", "PAY_NOW"))));

        OrderResponse response;
        try {
            response = client.post()
                    .uri("/v2/checkout/orders")
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    // Makes a retried create return the same order rather than
                    // a second one, if the first response was lost in transit.
                    .header("PayPal-Request-Id", "solarupgrade-order-" + ourOrderId)
                    .body(body)
                    .retrieve()
                    .body(OrderResponse.class);
        } catch (RestClientException ex) {
            throw new PaymentException("Could not create a PayPal order for order " + ourOrderId, ex);
        }

        if (response == null || response.id() == null) {
            throw new PaymentException("PayPal did not return an order id");
        }
        String approvalUrl = response.links() == null ? null : response.links().stream()
                .filter(link -> "payer-action".equals(link.rel()) || "approve".equals(link.rel()))
                .map(Link::href)
                .findFirst()
                .orElse(null);
        if (approvalUrl == null) {
            throw new PaymentException("PayPal returned no approval link for order " + response.id());
        }
        return new CreatedOrder(response.id(), approvalUrl);
    }

    /**
     * Captures a previously approved order.
     *
     * <p>This is a server-to-server call we make ourselves, so its answer is
     * evidence -- unlike the buyer's browser arriving back at a return URL,
     * which only says somebody loaded a page.
     *
     * <p>A second capture of an already-captured order is answered by PayPal
     * with an error rather than a second charge; the caller treats an order
     * that is already paid as nothing to do.
     */
    public Capture capture(String payPalOrderId) {
        CaptureResponse response;
        try {
            response = client.post()
                    .uri("/v2/checkout/orders/{id}/capture", payPalOrderId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(CaptureResponse.class);
        } catch (RestClientException ex) {
            // Never assume a capture that errored actually succeeded. The
            // order stays unpaid and the buyer can try again; PayPal's own
            // idempotency stops a double charge.
            throw new PaymentException("Could not capture PayPal order " + payPalOrderId, ex);
        }

        if (response == null || response.status() == null) {
            throw new PaymentException("PayPal returned no capture status for " + payPalOrderId);
        }
        return new Capture("COMPLETED".equals(response.status()), response.status(),
                capturedId(response), capturedAmount(response), capturedCurrency(response));
    }

    /**
     * Sends a captured payment back in full.
     *
     * <p>Against the capture id rather than the order id: an order can hold
     * more than one capture, and PayPal's refund endpoint addresses the
     * movement of money, not the order around it.
     *
     * <p>No amount is sent, which is how PayPal is told to refund the lot. A
     * figure calculated here could disagree with what was actually taken --
     * after a conversion, by a rounding -- and the customer is owed exactly
     * what left their account, not our arithmetic about it.
     */
    public Refund refund(String captureId) {
        RefundResponse response;
        try {
            response = client.post()
                    .uri("/v2/payments/captures/{id}/refund", captureId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(RefundResponse.class);
        } catch (RestClientException ex) {
            // Never report a refund that errored as done. The order stays paid
            // and visibly un-refunded, which is the state somebody can act on.
            throw new PaymentException("Could not refund PayPal capture " + captureId, ex);
        }

        if (response == null || response.status() == null) {
            throw new PaymentException("PayPal returned no refund status for " + captureId);
        }
        // COMPLETED is money returned. PENDING happens and is not a failure,
        // but it is not done either, and saying otherwise would tell a customer
        // their money is back before it is.
        return new Refund("COMPLETED".equals(response.status()), response.status(),
                response.id(),
                response.amount() == null ? null : new BigDecimal(response.amount().value()),
                response.amount() == null ? null : response.amount().currency_code());
    }

    /**
     * Asks PayPal whether a webhook really came from PayPal.
     *
     * <p>Without this the endpoint is an open invitation: anyone who guesses
     * the URL could post a "payment completed" body and get goods dispatched.
     */
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (!canVerifyWebhooks()) {
            return false;
        }
        try {
            VerifyResponse response = client.post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifyPayload(headers, rawBody))
                    .retrieve()
                    .body(VerifyResponse.class);
            return response != null && "SUCCESS".equals(response.verification_status());
        } catch (Exception ex) {
            // A verification that errors is a verification that failed. Never
            // fall through to trusting the payload.
            log.warn("PayPal webhook signature verification failed", ex);
            return false;
        }
    }

    /**
     * The verify call wants webhook_event as the event object itself, not as a
     * string, so the JSON is assembled by hand around the untouched raw body.
     */
    private String verifyPayload(Map<String, String> headers, String rawBody) {
        return "{"
                + "\"auth_algo\":\"" + escape(headers.getOrDefault("paypal-auth-algo", "")) + "\","
                + "\"cert_url\":\"" + escape(headers.getOrDefault("paypal-cert-url", "")) + "\","
                + "\"transmission_id\":\"" + escape(headers.getOrDefault("paypal-transmission-id", "")) + "\","
                + "\"transmission_sig\":\"" + escape(headers.getOrDefault("paypal-transmission-sig", "")) + "\","
                + "\"transmission_time\":\"" + escape(headers.getOrDefault("paypal-transmission-time", "")) + "\","
                + "\"webhook_id\":\"" + escape(webhookId) + "\","
                + "\"webhook_event\":" + rawBody
                + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * The id of the capture itself, which is what a refund is made against.
     *
     * <p>It used to be thrown away: only the amount and currency were read out
     * of the response. Refunds could not be built until it was kept, because
     * PayPal refunds a capture and nothing else identifies one.
     */
    private static String capturedId(CaptureResponse response) {
        return firstCapture(response).map(CaptureDetail::id).orElse(null);
    }

    private static BigDecimal capturedAmount(CaptureResponse response) {
        return firstCapture(response).map(c -> new BigDecimal(c.amount().value())).orElse(null);
    }

    private static String capturedCurrency(CaptureResponse response) {
        return firstCapture(response).map(c -> c.amount().currency_code()).orElse(null);
    }

    private static Optional<CaptureDetail> firstCapture(CaptureResponse response) {
        if (response.purchase_units() == null) {
            return Optional.empty();
        }
        return response.purchase_units().stream()
                .filter(unit -> unit.payments() != null && unit.payments().captures() != null)
                .flatMap(unit -> unit.payments().captures().stream())
                .filter(capture -> capture.amount() != null && capture.amount().value() != null)
                .findFirst();
    }

    /** What PayPal gave us back for a newly created order. */
    public record CreatedOrder(String id, String approvalUrl) {
    }

    /** The outcome of a capture attempt, and what was actually taken. */
    public record Capture(boolean completed, String status, String id,
                          BigDecimal amount, String currency) {
    }

    /** @param id PayPal's id for the refund, kept for reconciliation */
    public record Refund(boolean completed, String status, String id,
                         BigDecimal amount, String currency) {
    }

    // --- Wire shapes. Field names match PayPal's JSON. ----------------------

    record TokenResponse(String access_token, long expires_in) {
    }

    record OrderResponse(String id, String status, List<Link> links) {
    }

    record Link(String href, String rel) {
    }

    record CaptureResponse(String id, String status, List<PurchaseUnit> purchase_units) {
    }

    record PurchaseUnit(Payments payments) {
    }

    record Payments(List<CaptureDetail> captures) {
    }

    record CaptureDetail(String id, String status, Amount amount) {
    }

    record Amount(String currency_code, String value) {
    }

    record RefundResponse(String id, String status, Amount amount) {
    }

    record VerifyResponse(String verification_status) {
    }
}
