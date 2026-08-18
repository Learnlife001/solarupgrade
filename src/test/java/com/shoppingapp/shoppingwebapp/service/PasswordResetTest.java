package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class PasswordResetTest {

    private static final String EMAIL = "reset-test@example.test";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Spied rather than mocked: the real service still runs (and logs that mail
     * is unconfigured), and the token is captured on its way out — the only
     * place it exists in the clear.
     */
    @MockitoSpyBean
    private EmailService emailService;

    private User account;

    @BeforeEach
    void setUp() {
        account = userRepository.save(
                new User(EMAIL, passwordEncoder.encode("original-password-1"), "Reset Tester"));
        account.markEmailVerified();
        userRepository.save(account);
    }

    /**
     * Reads the token out of the email that was sent — the newest one, since a
     * test may ask for more than one link.
     */
    private String issuedToken() {
        return issuedTokenFor(EMAIL);
    }

    private String issuedTokenFor(String email) {
        userService.requestPasswordReset(email);
        ArgumentCaptor<String> token = forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordReset(any(), token.capture());
        return token.getValue();
    }

    @Test
    void aResetLinkSetsANewPassword() {
        String token = issuedToken();

        assertThat(userService.resetPassword(token, "a-brand-new-password")).isPresent();

        User reloaded = userRepository.findById(account.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("a-brand-new-password", reloaded.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("original-password-1", reloaded.getPassword())).isFalse();
    }

    /**
     * The token is a credential. Anyone who reads the database must not come
     * away with a working reset link.
     */
    @Test
    void theTokenItselfIsNeverStored() {
        String token = issuedToken();

        User reloaded = userRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getResetTokenHash())
                .isNotNull()
                .isNotEqualTo(token)
                .doesNotContain(token)
                // SHA-256 as lower-case hex.
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void aResetLinkOnlyWorksOnce() {
        String token = issuedToken();
        assertThat(userService.resetPassword(token, "first-new-password")).isPresent();

        assertThat(userService.resetPassword(token, "second-new-password")).isEmpty();

        User reloaded = userRepository.findById(account.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("first-new-password", reloaded.getPassword())).isTrue();
    }

    @Test
    void anExpiredLinkIsRefused() {
        String token = issuedToken();
        User stored = userRepository.findById(account.getId()).orElseThrow();
        stored.issueResetToken(stored.getResetTokenHash(), Instant.now().minus(1, ChronoUnit.MINUTES));
        userRepository.save(stored);

        assertThat(userService.userForResetToken(token)).isEmpty();
        assertThat(userService.resetPassword(token, "should-not-apply")).isEmpty();
    }

    @Test
    void aMadeUpTokenIsRefused() {
        issuedToken();

        assertThat(userService.resetPassword("not-a-real-token", "should-not-apply")).isEmpty();
        assertThat(userService.userForResetToken(null)).isEmpty();
        assertThat(userService.userForResetToken("")).isEmpty();
    }

    /** Asking twice must not leave the first link working. */
    @Test
    void requestingASecondLinkRetiresTheFirst() {
        String first = issuedToken();
        String second = issuedToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(userService.userForResetToken(first)).isEmpty();
        assertThat(userService.userForResetToken(second)).isPresent();
    }

    /** No account, no email, no exception, and nothing that says which it was. */
    @Test
    void anUnknownAddressIsSilentlyIgnored() {
        userService.requestPasswordReset("nobody-here@example.test");

        verify(emailService, never()).sendPasswordReset(any(), any());
    }

    /**
     * Following a link we emailed proves control of the mailbox, so the account
     * ends up verified. Otherwise someone could reset their password and still
     * be unable to sign in.
     */
    @Test
    void resettingAlsoVerifiesAnUnverifiedAccount() {
        User unverified = userRepository.save(
                new User("unverified-reset@example.test", passwordEncoder.encode("original-password-1"), "New"));
        assertThat(unverified.isEmailVerified()).isFalse();

        userService.resetPassword(issuedTokenFor("unverified-reset@example.test"), "a-brand-new-password");

        assertThat(userRepository.findById(unverified.getId()).orElseThrow().isEmailVerified()).isTrue();
    }
}
