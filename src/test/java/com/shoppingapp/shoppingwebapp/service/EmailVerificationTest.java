package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EmailVerificationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    private static RegistrationForm form(String email) {
        RegistrationForm f = new RegistrationForm();
        f.setFullName("New Person");
        f.setEmail(email);
        f.setPassword("sunny-rooftop-42");
        f.setConfirmPassword("sunny-rooftop-42");
        return f;
    }

    private User reload(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void registrationCreatesAnUnverifiedAccountWithASixDigitCode() {
        User user = userService.register(form("fresh@example.test"));

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getVerificationCode()).hasSize(6).containsOnlyDigits();
        assertThat(user.getVerificationCodeExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void anUnverifiedAccountCannotSignIn() {
        userService.register(form("unverified@example.test"));

        UserDetails details = userDetailsService.loadUserByUsername("unverified@example.test");

        // Spring Security refuses a disabled account before checking the password.
        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void theRightCodeVerifiesAndEnablesTheAccount() {
        User user = userService.register(form("confirms@example.test"));

        assertThat(userService.verify("confirms@example.test", user.getVerificationCode())).isPresent();

        assertThat(reload("confirms@example.test").isEmailVerified()).isTrue();
        assertThat(userDetailsService.loadUserByUsername("confirms@example.test").isEnabled()).isTrue();
    }

    @Test
    void theCodeIsSingleUse() {
        User user = userService.register(form("once@example.test"));
        String code = user.getVerificationCode();

        assertThat(userService.verify("once@example.test", code)).isPresent();
        assertThat(userService.verify("once@example.test", code)).isEmpty();
    }

    @Test
    void aWrongCodeIsRejectedAndCounted() {
        userService.register(form("wrong@example.test"));

        assertThat(userService.verify("wrong@example.test", "000000")).isEmpty();

        User reloaded = reload("wrong@example.test");
        assertThat(reloaded.isEmailVerified()).isFalse();
        assertThat(reloaded.getVerificationAttempts()).isEqualTo(1);
    }

    @Test
    void theCodeIsBurnedAfterTooManyWrongGuesses() {
        User user = userService.register(form("bruteforce@example.test"));
        String realCode = user.getVerificationCode();
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < User.MAX_VERIFICATION_ATTEMPTS; i++) {
            assertThat(userService.verify("bruteforce@example.test", wrongCode)).isEmpty();
        }

        // Even the correct code no longer works: guessing has to start over
        // with a freshly issued one.
        assertThat(userService.verify("bruteforce@example.test", realCode)).isEmpty();
        assertThat(reload("bruteforce@example.test").isEmailVerified()).isFalse();
    }

    @Test
    void anExpiredCodeIsRejected() {
        User user = userService.register(form("stale@example.test"));
        String code = user.getVerificationCode();
        user.issueVerificationCode(code, Instant.now().minusSeconds(1));
        userRepository.save(user);

        assertThat(userService.verify("stale@example.test", code)).isEmpty();
        assertThat(reload("stale@example.test").isEmailVerified()).isFalse();
    }

    @Test
    void aCodeOnlyWorksForTheAccountItWasIssuedTo() {
        User a = userService.register(form("owner@example.test"));
        userService.register(form("other@example.test"));

        // Six digits are not unique, so the code must be checked against one
        // named account rather than matched across all of them.
        assertThat(userService.verify("other@example.test", a.getVerificationCode())).isEmpty();
        assertThat(reload("other@example.test").isEmailVerified()).isFalse();
    }

    @Test
    void unknownAddressesAndBlankInputAreRejected() {
        assertThat(userService.verify("nobody@example.test", "123456")).isEmpty();
        assertThat(userService.verify("", "123456")).isEmpty();
        assertThat(userService.verify("nobody@example.test", "")).isEmpty();
        assertThat(userService.verify(null, null)).isEmpty();
    }

    @Test
    void resendIssuesAFreshCodeAndRestoresTheAttemptAllowance() {
        User user = userService.register(form("resend@example.test"));
        String first = user.getVerificationCode();
        userService.verify("resend@example.test", "000000");

        userService.resendVerification("resend@example.test");

        User reloaded = reload("resend@example.test");
        assertThat(reloaded.getVerificationCode()).hasSize(6).isNotEqualTo(first);
        assertThat(reloaded.getVerificationAttempts()).isZero();
    }

    @Test
    void resendDoesNothingForAnAlreadyVerifiedAccount() {
        User user = userService.register(form("done@example.test"));
        userService.verify("done@example.test", user.getVerificationCode());

        userService.resendVerification("done@example.test");

        User reloaded = reload("done@example.test");
        assertThat(reloaded.isEmailVerified()).isTrue();
        assertThat(reloaded.getVerificationCode()).isNull();
    }

    @Test
    void resendForAnUnknownAddressIsSilent() {
        // Must not throw: that would let the form reveal which addresses exist.
        userService.resendVerification("nobody@example.test");
    }
}
