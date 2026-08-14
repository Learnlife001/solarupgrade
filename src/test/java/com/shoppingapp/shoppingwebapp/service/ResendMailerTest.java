package com.shoppingapp.shoppingwebapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real HTTP call against a stub standing in for api.resend.com,
 * so the request shape is verified rather than assumed.
 */
class ResendMailerTest {

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private volatile int responseStatus = 200;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] out = "{\"id\":\"stub-id\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private ResendMailer mailer() {
        return new ResendMailer(RestClient.builder(), "re_test_key", baseUrl);
    }

    @Test
    void postsToTheEmailsEndpointWithBearerAuth() {
        mailer().send("SolarUpgrade <orders@example.test>", "buyer@example.test", "Subject", "Body");

        assertThat(capturedPath.get()).isEqualTo("/emails");
        assertThat(capturedAuth.get()).isEqualTo("Bearer re_test_key");
        assertThat(capturedContentType.get()).startsWith("application/json");
    }

    @Test
    void sendsTheFieldNamesResendExpects() throws Exception {
        mailer().send("SolarUpgrade <orders@example.test>", "buyer@example.test", "Your order", "Thanks!");

        JsonNode body = new ObjectMapper().readTree(capturedBody.get());
        assertThat(body.get("from").asText()).isEqualTo("SolarUpgrade <orders@example.test>");
        // Resend takes "to" as an array even for a single recipient.
        assertThat(body.get("to").isArray()).isTrue();
        assertThat(body.get("to").get(0).asText()).isEqualTo("buyer@example.test");
        assertThat(body.get("subject").asText()).isEqualTo("Your order");
        assertThat(body.get("text").asText()).isEqualTo("Thanks!");
    }

    @Test
    void surfacesAnErrorResponseToTheCaller() {
        responseStatus = 422;

        assertThatThrownBy(() -> mailer().send("from@example.test", "to@example.test", "s", "b"))
                .isInstanceOf(RestClientResponseException.class);
    }
}
