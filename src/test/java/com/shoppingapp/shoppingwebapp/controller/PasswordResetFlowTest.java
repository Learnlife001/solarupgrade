package com.shoppingapp.shoppingwebapp.controller;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.security.RateLimiter;
import com.shoppingapp.shoppingwebapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowTest {

    private static final String EMAIL = "reset-flow@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private EmailService emailService;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // The limiter is a shared bean with a one-hour window, so without this
        // the fourth test of the run is refused rather than exercised. Found
        // by writing the tests: three passed and the rest got a 429.
        rateLimiter.resetAll();
        User user = userRepository.findByEmail(EMAIL).orElseGet(() -> userRepository.save(
                new User(EMAIL, passwordEncoder.encode("original-password-1"), "Flow Tester")));
        user.markEmailVerified();
        userRepository.save(user);
    }

    private String requestLink(String email) throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", email).with(csrf()))
                .andExpect(redirectedUrl("/login"));
        ArgumentCaptor<String> token = forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordReset(any(), token.capture());
        return token.getValue();
    }

    /** The pages have to be reachable without signing in, or they are useless. */
    @Test
    void bothPagesAreOpenToSignedOutVisitors() throws Exception {
        mockMvc.perform(get("/forgot-password")).andExpect(status().isOk());
        mockMvc.perform(get("/reset-password").param("token", "nonsense")).andExpect(status().isOk());
    }

    @Test
    void theWholeFlowWorksFromTheFormToTheNewPassword() throws Exception {
        String token = requestLink(EMAIL);

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Set a new password")));

        mockMvc.perform(post("/reset-password")
                        .param("token", token)
                        .param("password", "a-brand-new-password")
                        .param("confirmPassword", "a-brand-new-password")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login"));

        assertThat(passwordEncoder.matches("a-brand-new-password",
                userRepository.findByEmail(EMAIL).orElseThrow().getPassword())).isTrue();
    }

    /**
     * An unknown address must produce the same page as a known one. Anything
     * else turns the form into a way to test whether someone shops here.
     */
    @Test
    void anUnknownAddressGetsTheSameAnswerAsAKnownOne() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "definitely-nobody@example.test")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void aDeadLinkExplainsItselfInsteadOfShowingAForm() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", "not-a-real-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no longer valid")));
    }

    /** The reset form must not be a way around the password rules. */
    @Test
    void aWeakPasswordIsRefusedOnTheResetFormToo() throws Exception {
        String token = requestLink(EMAIL);

        mockMvc.perform(post("/reset-password")
                        .param("token", token)
                        .param("password", "short")
                        .param("confirmPassword", "short")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("at least 10 characters")));

        assertThat(passwordEncoder.matches("original-password-1",
                userRepository.findByEmail(EMAIL).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void mismatchedConfirmationIsRefused() throws Exception {
        String token = requestLink(EMAIL);

        mockMvc.perform(post("/reset-password")
                        .param("token", token)
                        .param("password", "a-brand-new-password")
                        .param("confirmPassword", "a-different-password")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("do not match")));

        assertThat(passwordEncoder.matches("original-password-1",
                userRepository.findByEmail(EMAIL).orElseThrow().getPassword())).isTrue();
    }

    /** Posting a new password without the token must not change anything. */
    @Test
    void postingWithoutAValidTokenChangesNothing() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .param("token", "made-up")
                        .param("password", "a-brand-new-password")
                        .param("confirmPassword", "a-brand-new-password")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no longer valid")));

        assertThat(passwordEncoder.matches("original-password-1",
                userRepository.findByEmail(EMAIL).orElseThrow().getPassword())).isTrue();
    }

    /**
     * The reset request emails an address the caller names, so it is capped
     * like the other mail-sending endpoints. Without a cap it is a way to post
     * email to someone else, three at a time.
     */
    @Test
    void repeatedResetRequestsAreRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/forgot-password").param("email", EMAIL).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/forgot-password").param("email", EMAIL).with(csrf()))
                .andExpect(status().isTooManyRequests());
    }

    /** CSRF still applies: these are state-changing posts like any other. */
    @Test
    void bothPostsRequireACsrfToken() throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", EMAIL))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/reset-password")
                        .param("token", "x")
                        .param("password", "a-brand-new-password")
                        .param("confirmPassword", "a-brand-new-password"))
                .andExpect(status().isForbidden());
    }
}
