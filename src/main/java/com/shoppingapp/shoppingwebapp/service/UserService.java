package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.dto.RegistrationForm;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.support.Redact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /**
     * Short enough to read off a screen and type. A code this small is only
     * safe because attempts are capped and it expires quickly -- see
     * {@link User#MAX_VERIFICATION_ATTEMPTS}.
     */
    private static final int CODE_DIGITS = 6;

    /**
     * Long enough to find the email and follow the link, short enough that a
     * link left sitting in an inbox stops working. Shorter than a password
     * would live for, because this is the thing that replaces one.
     */
    private static final Duration RESET_LIFETIME = Duration.ofMinutes(30);

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(15);

    /** URL-safe and unpadded, so the token drops straight into a link. */
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

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
                log.info("Verification attempts exhausted for {}", Redact.email(user.getEmail()));
            }
            return Optional.empty();
        }

        user.markEmailVerified();
        log.info("Verified email for {}", Redact.email(user.getEmail()));
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

    /**
     * Emails a reset link, if that address has an account.
     *
     * <p>Silent about whether it does, for the same reason resendVerification
     * is: a form that answers "no such account" is a way to find out who shops
     * here. The caller gets the same page either way.
     *
     * <p>A token rather than a six-digit code. A code is fine for verification,
     * where the worst case is confirming an address someone already controls;
     * this one can change a password, so it is 256 bits of randomness and the
     * database stores only its hash.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            byte[] raw = new byte[32];
            random.nextBytes(raw);
            String token = BASE64.encodeToString(raw);

            user.issueResetToken(sha256(token), Instant.now().plus(RESET_LIFETIME));
            emailService.sendPasswordReset(userRepository.save(user), token);
            log.info("Issued a password reset for {}", Redact.email(user.getEmail()));
        });
    }

    /** The account a live reset token belongs to, if any. */
    public Optional<User> userForResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByResetTokenHash(sha256(token))
                .filter(user -> user.isResetTokenValid(Instant.now()));
    }

    /**
     * Sets a new password from a reset link and burns the token.
     *
     * @return the account, or empty when the token is unknown, expired or
     *         already used -- reported identically, because a caller holding a
     *         bad token learns nothing from which kind of bad it was.
     */
    @Transactional
    public Optional<User> resetPassword(String token, String newPassword) {
        return userForResetToken(token).map(user -> {
            user.applyPasswordReset(passwordEncoder.encode(newPassword));
            log.info("Password reset completed for {}", Redact.email(user.getEmail()));
            return userRepository.save(user);
        });
    }

    /** Zero-padded so every code is exactly six digits, "004271" included. */
    private String newCode() {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", random.nextInt(bound));
    }

    /**
     * Lower-case hex, so the stored value is exactly the 64 characters the
     * column is sized for and two runs of this method always agree.
     *
     * <p>A plain hash, not bcrypt, and that is not an oversight: bcrypt exists
     * to slow down guessing a password a human chose, and there is nothing to
     * guess here. The input is 32 random bytes.
     */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM ships SHA-256; the checked exception is a formality.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
