package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.service.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * matter most: nothing it is sent may move an order without PayPal having
 * confirmed it signed the message.
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

    /** The whole point: an unsigned body is refused, however well-formed. */
    @Test
    void aForgedWebhookSettlesNothing() throws Exception {
        when(paymentService.webhookVerificationConfigured()).thenReturn(true);
        when(paymentService.verifyWebhook(any(), anyString())).thenReturn(false);

        mockMvc.perform(post("/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_EVENT))
                .andExpect(status().isOk());

        verify(paymentService, never()).settleFromWebhook(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void aVerifiedCaptureEventSettlesTheOrder() throws Exception {
        when(paymentService.webhookVerificationConfigured()).thenReturn(true);
        when(paymentService.verifyWebhook(any(), anyString())).thenReturn(true);

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
        when(paymentService.webhookVerificationConfigured()).thenReturn(false);

        mockMvc.perform(post("/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_EVENT))
                .andExpect(status().isOk());

        verify(paymentService, never()).settleFromWebhook(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void anUnrelatedVerifiedEventIsIgnored() throws Exception {
        when(paymentService.webhookVerificationConfigured()).thenReturn(true);
        when(paymentService.verifyWebhook(any(), anyString())).thenReturn(true);

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
