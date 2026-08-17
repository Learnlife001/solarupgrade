package com.shoppingapp.shoppingwebapp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Locks an account for a cooldown after repeated wrong passwords.
 *
 * <p>This is the second layer, not the first. {@link RateLimitFilter} already
 * caps attempts per caller; this one follows the account instead, so moving
 * between addresses does not buy an attacker a fresh allowance.
 *
 * <p><b>The trade-off, on purpose.</b> Anything that locks an account on failed
 * attempts hands a stranger a way to lock someone out on demand, just by
 * guessing their email. That is why the lock is a short cooldown rather than
 * something needing support to undo, and why the count clears the moment a
 * correct password arrives. Fifteen minutes makes guessing hopeless while
 * costing a customer who mistyped almost nothing.
 *
 * <p>Only wrong passwords count. An unverified account is refused for a reason
 * that has nothing to do with guessing, so it must not accumulate towards a
 * lock -- otherwise a new customer who forgot to check their inbox locks
 * themselves out by trying twice.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final RateLimiter rateLimiter;
    private final int maxFailures;
    private final Duration cooldown;

    public LoginAttemptService(RateLimiter rateLimiter,
                               @Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.cooldown-minutes:15}") long cooldownMinutes) {
        this.rateLimiter = rateLimiter;
        this.maxFailures = maxFailures;
        this.cooldown = Duration.ofMinutes(cooldownMinutes);
    }

    public void recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        rateLimiter.tryAcquire(key(email), policy());
        if (isLocked(email)) {
            log.warn("Account {} locked for {} minutes after {} failed sign-ins",
                    normalise(email), cooldown.toMinutes(), maxFailures);
        }
    }

    /** Called on a successful sign-in, so earlier typos are forgotten. */
    public void recordSuccess(String email) {
        if (email != null && !email.isBlank()) {
            rateLimiter.reset(key(email));
        }
    }

    public boolean isLocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return rateLimiter.attempts(key(email)) >= maxFailures;
    }

    public Duration remainingCooldown(String email) {
        return rateLimiter.retryAfter(key(email), policy());
    }

    private RateLimiter.Policy policy() {
        return new RateLimiter.Policy(maxFailures, cooldown);
    }

    private static String key(String email) {
        return "login-failures|" + normalise(email);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
