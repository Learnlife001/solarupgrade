package com.shoppingapp.shoppingwebapp.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * PayPal, as one {@link PaymentProvider} among however many a deployment has.
 *
 * <p>A thin adapter over {@link PayPalClient}, which keeps the HTTP calls,
 * the OAuth token and PayPal's own record shapes. The split is deliberate: the
 * client speaks PayPal, this class speaks the shop's language, and the
 * vocabulary of one stops at the boundary of the other. Everything PayPal-
 * specific — {@code custom_id} carrying our order id, the capture living under
 * {@code purchase_units}, the five headers a signature needs — is on this side
 * of that line and none of it reaches {@code PaymentService}.
 *
 * <p>Registered only when {@link PayPalClient} is, which is only when
 * credentials are configured. No credentials means no bean, no provider, and
 * no PayPal option on the checkout.
 */
@Component
public class PayPalProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PayPalProvider.class);

    /** The five headers PayPal signs a webhook with. */
    private static final String[] SIGNATURE_HEADERS = {
            "paypal-auth-algo", "paypal-cert-url", "paypal-transmission-id",
            "paypal-transmission-sig", "paypal-transmission-time"};

    /** The only event that means money moved. PayPal sends many others. */
    private static final String CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";

    private final org.springframework.beans.factory.ObjectProvider<PayPalClient> client;
    private final ObjectMapper objectMapper;

    public PayPalProvider(org.springframework.beans.factory.ObjectProvider<PayPalClient> client,
                          ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "paypal";
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public boolean isConfigured() {
        return client.getIfAvailable() != null;
    }

    @Override
    public Checkout begin(Order order, String returnUrl, String cancelUrl) {
        PayPalClient.CreatedOrder created = require().createOrder(
                order.getId(),
                order.getPaymentAmount(),
                order.getPaymentCurrency(),
                returnUrl,
                cancelUrl);
        return new Checkout(created.id(), created.approvalUrl());
    }

    @Override
    public CaptureResult capture(Order order) {
        PayPalClient.Capture capture = require().capture(order.getProviderReference());
        return new CaptureResult(capture.completed(), capture.status(),
                capture.amount(), capture.currency());
    }

    @Override
    public boolean canVerifyWebhooks() {
        PayPalClient payPal = client.getIfAvailable();
        return payPal != null && payPal.canVerifyWebhooks();
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        PayPalClient payPal = client.getIfAvailable();
        return payPal != null && payPal.verifyWebhook(headers, rawBody);
    }

    @Override
    public String[] signatureHeaders() {
        return SIGNATURE_HEADERS.clone();
    }

    @Override
    public Optional<PaymentEvent> readWebhook(String rawBody) {
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            if (!CAPTURE_COMPLETED.equals(event.path("event_type").asText(""))) {
                return Optional.empty();
            }

            JsonNode resource = event.path("resource");
            Long orderId = parseOrderId(resource.path("custom_id").asText(null));
            if (orderId == null) {
                log.warn("PayPal webhook carried no usable custom_id");
                return Optional.empty();
            }

            return Optional.of(new PaymentEvent(
                    orderId,
                    resource.path("supplementary_data").path("related_ids").path("order_id").asText(null),
                    amountOf(resource),
                    resource.path("amount").path("currency_code").asText(null)));
        } catch (Exception ex) {
            log.error("Could not read a verified PayPal webhook", ex);
            return Optional.empty();
        }
    }

    private PayPalClient require() {
        PayPalClient payPal = client.getIfAvailable();
        if (payPal == null) {
            throw new PaymentException("PayPal is not configured on this deployment");
        }
        return payPal;
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
