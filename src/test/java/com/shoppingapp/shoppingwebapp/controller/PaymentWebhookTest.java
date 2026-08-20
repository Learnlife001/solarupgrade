package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.service.payment.PaymentProvider;
import com.shoppingapp.shoppingwebapp.service.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook endpoint is open to the internet, so these are the tests that
 * matter most: nothing it is sent may move an order without the provider
 * having confirmed it signed the message.
 *
 * <p>The provider is a mock rather than PayPal specifically. The endpoint is
 * now shared by every provider, and what is being tested is the rule it
 * enforces for all of them -- verify first, act second -- not one provider's
 * JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookTest {

    private static final String CAPTURE_EVENT = """
            {
              "event_type": "PAYMENT.CAPTURE.COMPLETED",
              "resource": {
                "custom_id": "1",
                "amount": { "currency_code": "EUR", "value": "211.11" },
                "supplementary_data": { "related_ids": { "order_id": "PP-1" } }
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private PaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = org.mockito.Mockito.mock(PaymentProvider.class);
        when(provider.id()).thenReturn("paypal");
        when(provider.signatureHeaders()).thenReturn(new String[]{"paypal-transmission-sig"});
        when(paymentService.byId("paypal")).thenReturn(Optional.of(provider));
    }

    /** What a provider hands back once it has read its own verified body. */
    private void providerReads(String reference) {
        when(provider.readWebhook(anyString())).thenReturn(Optional.of(
                new PaymentProvider.PaymentEvent(1L, reference, new BigDecimal("211.11"), "EUR")));
    }

    /** The whole point: an unsigned body is refused, however well-formed. */
    @Test
    void aForgedWebhookSettlesNothing() throws Exception {
        when(provider.canVerifyWebhooks()).thenReturn(true);
        when(provider.verifyWebhook(any(), anyString())).thenReturn(false);
        providerReads("PP-1");

        mockMvc.perform(post("/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_EVENT))
                .andExpect(status().isOk());

        verify(paymentService, never()).settleFromWebhook(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void aVerifiedCaptureEventSettlesTheOrder() throws Exception {
        when(provider.canVerifyWebhooks()).thenReturn(true);
        when(provider.verifyWebhook(any(), anyString())).thenReturn(true);
        providerReads("PP-1");

        mockMvc.perform(post("/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_EVENT))
                .andExpect(status().isOk());

        verify(paymentService).settleFromWebhook(anyLong(), anyString(), any(), anyString());
    }

    /**
     * With no webhook id there is no way to tell genuine from forged, so the
     * endpoint must ignore everything rather than assume the best.
     */
    @Test
    void withNoWebhookIdConfiguredNothingIsActedOn() throws Exception {
        when(provider.canVerifyWebhooks()).thenReturn(false);
        providerReads("PP-1");

        mockMvc.perform(post("/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_EVENT))
                .andExpect(status().isOk());

        verify(paymentService, never()).settleFromWebhook(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void anUnrelatedVerifiedEventIsIgnored() throws Exception {
        when(provider.canVerifyWebhooks()).thenReturn(true);
        when(provider.verifyWebhook(any(), anyString())).thenReturn(true);
        // The provider recognises nothing worth acting on in this body.
        when(provider.readWebhook(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"BILLING.SUBSCRIPTION.CREATED\",\"resource\":{}}"))
                .andExpect(status().isOk());

        verify(paymentService, never()).settleFromWebhook(anyLong(), anyString(), any(), anyString());
    }

    /**
     * The webhook is exempt from CSRF because a provider has no token to send.
     * The return URL is not, and it is a page, so it stays behind the login.
     */
    @Test
    void theReturnUrlRequiresSigningIn() throws Exception {
        mockMvc.perform(get("/payments/paypal/return").param("order", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
