package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Long enough that guessing is hopeless; short enough for a tidy URL. */
    private static final int TOKEN_BYTES = 32;
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No user with email " + email));
    }

    public boolean emailTaken(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Creates the account in an unverified state and emails a verification
     * link. The account cannot sign in until the link is followed, which is
     * what stops a made-up address from becoming a usable account.
     */
    @Transactional
    public User register(RegistrationForm form) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("An account with that email already exists");
        }
        User user = new User(
                form.getEmail().trim().toLowerCase(),
                passwordEncoder.encode(form.getPassword()),
                form.getFullName().trim());
        user.issueVerificationToken(newToken(), Instant.now().plus(TOKEN_LIFETIME));

        User saved = userRepository.save(user);
        emailService.sendVerification(saved);
        return saved;
    }

    /**
     * Consumes a verification link.
     *
     * @return the verified user, or empty when the token is unknown, already
     *         used or expired -- all reported the same way, so the endpoint
     *         cannot be used to probe which tokens exist.
     */
    @Transactional
    public Optional<User> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByVerificationToken(token)
                .filter(user -> user.isVerificationTokenValid(Instant.now()))
                .map(user -> {
                    user.markEmailVerified();
                    log.info("Verified email for {}", user.getEmail());
                    return userRepository.save(user);
                });
    }

    /**
     * Issues a fresh link for an account that has not been verified yet.
     *
     * <p>Deliberately silent about whether the address exists: it always
     * appears to succeed, so the form cannot be used to discover which
     * addresses are registered.
     */
    @Transactional
    public void resendVerification(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    user.issueVerificationToken(newToken(), Instant.now().plus(TOKEN_LIFETIME));
                    emailService.sendVerification(userRepository.save(user));
                });
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
