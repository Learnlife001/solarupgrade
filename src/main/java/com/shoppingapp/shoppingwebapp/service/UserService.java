package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /**
     * Short enough to read off a screen and type. A code this small is only
     * safe because attempts are capped and it expires quickly -- see
     * {@link User#MAX_VERIFICATION_ATTEMPTS}.
     */
    private static final int CODE_DIGITS = 6;
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(15);

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
        user.issueVerificationCode(newCode(), Instant.now().plus(CODE_LIFETIME));

        User saved = userRepository.save(user);
        emailService.sendVerification(saved);
        return saved;
    }

    /**
     * Consumes a verification code.
     *
     * <p>The code is looked up against one specific account rather than
     * globally: a six-digit value is not unique enough to identify a user, and
     * matching it across all accounts would mean any correct-looking guess
     * verified somebody.
     *
     * @return the verified user, or empty when the address is unknown, the code
     *         is wrong, expired, or the attempt allowance is spent -- all
     *         reported identically so the form reveals nothing.
     */
    @Transactional
    public Optional<User> verify(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return Optional.empty();
        }
        Optional<User> found = userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user -> !user.isEmailVerified());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        User user = found.get();
        if (!user.isVerificationCodeValid(Instant.now())) {
            return Optional.empty();
        }

        // Constant-time compare: the codes are equal length, and this costs
        // nothing next to leaking a per-digit timing signal.
        if (!MessageDigest.isEqual(
                user.getVerificationCode().getBytes(StandardCharsets.UTF_8),
                code.trim().getBytes(StandardCharsets.UTF_8))) {
            boolean exhausted = user.recordFailedVerification();
            userRepository.save(user);
            if (exhausted) {
                log.info("Verification attempts exhausted for {}", user.getEmail());
            }
            return Optional.empty();
        }

        user.markEmailVerified();
        log.info("Verified email for {}", user.getEmail());
        return Optional.of(userRepository.save(user));
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
                    user.issueVerificationCode(newCode(), Instant.now().plus(CODE_LIFETIME));
                    emailService.sendVerification(userRepository.save(user));
                });
    }

    /** Zero-padded so every code is exactly six digits, "004271" included. */
    private String newCode() {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", random.nextInt(bound));
    }
}
