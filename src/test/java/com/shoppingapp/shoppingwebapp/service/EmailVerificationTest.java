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
import java.util.Optional;

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
        f.setPassword("password123");
        f.setConfirmPassword("password123");
        return f;
    }

    @Test
    void registrationCreatesAnUnverifiedAccountWithAToken() {
        User user = userService.register(form("fresh@example.test"));

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getVerificationToken()).isNotBlank();
        assertThat(user.getVerificationTokenExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void anUnverifiedAccountCannotSignIn() {
        userService.register(form("unverified@example.test"));

        UserDetails details = userDetailsService.loadUserByUsername("unverified@example.test");

        // Spring Security refuses a disabled account before checking the password.
        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void followingTheLinkVerifiesAndEnablesTheAccount() {
        User user = userService.register(form("confirms@example.test"));

        Optional<User> verified = userService.verify(user.getVerificationToken());

        assertThat(verified).isPresent();
        assertThat(verified.get().isEmailVerified()).isTrue();
        assertThat(userDetailsService.loadUserByUsername("confirms@example.test").isEnabled()).isTrue();
    }

    @Test
    void theLinkIsSingleUse() {
        User user = userService.register(form("once@example.test"));
        String token = user.getVerificationToken();

        assertThat(userService.verify(token)).isPresent();
        // Token was burned on first use, so replaying the link does nothing.
        assertThat(userService.verify(token)).isEmpty();
    }

    @Test
    void anExpiredTokenIsRejected() {
        User user = userService.register(form("stale@example.test"));
        user.issueVerificationToken(user.getVerificationToken(), Instant.now().minusSeconds(1));
        userRepository.save(user);

        assertThat(userService.verify(user.getVerificationToken())).isEmpty();
        assertThat(userRepository.findByEmail("stale@example.test").orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void unknownAndEmptyTokensAreRejected() {
        assertThat(userService.verify("not-a-real-token")).isEmpty();
        assertThat(userService.verify("")).isEmpty();
        assertThat(userService.verify(null)).isEmpty();
    }

    @Test
    void resendIssuesAFreshTokenForAnUnverifiedAccount() {
        User user = userService.register(form("resend@example.test"));
        String first = user.getVerificationToken();

        userService.resendVerification("resend@example.test");

        String second = userRepository.findByEmail("resend@example.test").orElseThrow().getVerificationToken();
        assertThat(second).isNotBlank().isNotEqualTo(first);
    }

    @Test
    void resendDoesNothingForAnAlreadyVerifiedAccount() {
        User user = userService.register(form("done@example.test"));
        userService.verify(user.getVerificationToken());

        userService.resendVerification("done@example.test");

        User reloaded = userRepository.findByEmail("done@example.test").orElseThrow();
        assertThat(reloaded.isEmailVerified()).isTrue();
        assertThat(reloaded.getVerificationToken()).isNull();
    }

    @Test
    void resendForAnUnknownAddressIsSilent() {
        // Must not throw: that would let the form reveal which addresses exist.
        userService.resendVerification("nobody@example.test");
    }
}
