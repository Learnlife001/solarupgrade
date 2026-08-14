package com.shoppingapp.shoppingwebapp.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Sends through Resend's HTTP API rather than its SMTP endpoint.
 *
 * <p>Both are offered by Resend and the SMTP route would need no code at all,
 * just Spring's JavaMailSender. It is avoided because outbound SMTP is commonly
 * restricted on hosting platforms, and a blocked port 587 shows up as a
 * connection timeout at send time -- long after deploy, and only on the paths
 * that send mail. HTTPS on 443 is open everywhere the app can run at all.
 *
 * <p>Registered only when an API key is configured, so local development
 * without one falls back to logging (see {@link EmailService}).
 */
@Component
@ConditionalOnProperty(name = "app.mail.resend.api-key")
public class ResendMailer {

    private final RestClient client;
    private final String apiKey;

    public ResendMailer(RestClient.Builder builder,
                        @Value("${app.mail.resend.api-key}") String apiKey,
                        @Value("${app.mail.resend.base-url:https://api.resend.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.client = builder.baseUrl(baseUrl).build();
    }

    /**
     * @throws org.springframework.web.client.RestClientException if Resend
     *         rejects the message; the caller decides whether that is fatal.
     */
    public void send(String from, String to, String subject, String text) {
        client.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Payload(from, List.of(to), subject, text))
                .retrieve()
                .toBodilessEntity();
    }

    /** Field names match Resend's API; "text" sends a plain-text body. */
    record Payload(String from, List<String> to, String subject, String text) {
    }
}
