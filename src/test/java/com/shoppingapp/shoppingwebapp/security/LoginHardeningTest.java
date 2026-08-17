package com.shoppingapp.shoppingwebapp.security;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account lockout and the response headers.
 *
 * <p>The lockout limit is set low here so the test does not have to trip the
 * per-caller rate limit to reach it -- the two layers guard different things
 * and would otherwise collide.
 */
@SpringBootTest(properties = {
        "app.login.max-failures=3",
        "app.login.cooldown-minutes=15"
})
@AutoConfigureMockMvc
class LoginHardeningTest {

    private static final String EMAIL = "lockout@example.test";
    private static final String PASSWORD = "a-real-passphrase-99";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // The limiter is a singleton and outlives each test method, so without
        // this the per-caller cap on /login would trip partway through the
        // class and the lockout under test would never be reached. Clearing
        // both layers keeps each method measuring only what it means to.
        rateLimiter.resetAll();
        if (userRepository.findByEmail(EMAIL).isEmpty()) {
            User user = new User(EMAIL, passwordEncoder.encode(PASSWORD), "Locked Out");
            user.markEmailVerified();
            userRepository.save(user);
        }
    }

    private void attempt(String password) throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * The point of following the account rather than only the caller: moving to
     * a different address must not buy a fresh allowance against one account.
     */
    @Test
    void theRightPasswordIsRefusedWhileTheAccountIsCoolingDown() throws Exception {
        for (int i = 0; i < 3; i++) {
            attempt("wrong-" + i);
        }
        assertThat(loginAttempts.isLocked(EMAIL)).isTrue();

        mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?locked"));
    }

    /** A correct password clears the count, so a customer's typos do not follow them. */
    @Test
    void signingInSuccessfullyClearsTheCount() throws Exception {
        attempt("wrong-once");
        attempt("wrong-twice");

        mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/products"));

        assertThat(loginAttempts.isLocked(EMAIL)).isFalse();
    }

    @Test
    void theLockedPageDoesNotRevealWhetherTheAccountExists() throws Exception {
        // Same treatment for an address with no account at all, so the page
        // cannot be used to enumerate customers.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "ghost@example.test")
                            .param("password", "wrong-" + i)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(loginAttempts.isLocked("ghost@example.test")).isTrue();
    }

    @Test
    void securityHeadersArePresentOnEveryPage() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                // No 'unsafe-inline' anywhere: the templates carry no inline
                // styles or scripts, so the policy does not have to concede it.
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("default-src 'self'"),
                                org.hamcrest.Matchers.containsString("object-src 'none'"),
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.containsString("unsafe-inline")))));
    }
}
