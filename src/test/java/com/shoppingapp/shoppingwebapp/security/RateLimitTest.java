package com.shoppingapp.shoppingwebapp.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limits themselves are configuration and will be tuned; what these pin is
 * that a limit exists at all, that it is scoped to the endpoint being attacked,
 * and that ordinary browsing is untouched by it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void clearCounters() {
        // Shared singleton: another test class posting to /login first would
        // otherwise leave this one starting partway through its allowance.
        rateLimiter.resetAll();
    }

    /**
     * Three an hour, because each one sends an email. An unlimited loop here is
     * somebody else's money and the sending domain's reputation.
     */
    @Test
    void resendVerificationIsCappedTightly() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/resend-verification")
                            .param("email", "someone@example.test")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/resend-verification")
                        .param("email", "someone@example.test")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                // So anything automated is told when to come back.
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void repeatedSignInAttemptsAreEventuallyRefused() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "nobody@example.test")
                            .param("password", "wrong-guess-" + i)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/login")
                        .param("username", "nobody@example.test")
                        .param("password", "wrong-again")
                        .with(csrf()))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * A limiter that also throttles shopping would be worse than none, because
     * it would be turned off.
     */
    @Test
    void browsingIsNotRateLimited() throws Exception {
        for (int i = 0; i < 40; i++) {
            mockMvc.perform(get("/products")).andExpect(status().isOk());
        }
        // Reading the sign-in page costs nothing either; only posting it does.
        for (int i = 0; i < 40; i++) {
            mockMvc.perform(get("/login")).andExpect(status().isOk());
        }
    }

    @Test
    void aSuccessfulAttemptForgetsEarlierFailures() {
        RateLimiter.Policy policy = new RateLimiter.Policy(2, java.time.Duration.ofMinutes(5));

        rateLimiter.tryAcquire("test-key", policy);
        rateLimiter.tryAcquire("test-key", policy);
        // Third would be refused...
        rateLimiter.reset("test-key");

        // ...but a reset puts the caller back to nothing, which is what a
        // correct password does for a customer who mistyped twice.
        org.assertj.core.api.Assertions.assertThat(rateLimiter.tryAcquire("test-key", policy)).isTrue();
    }
}
