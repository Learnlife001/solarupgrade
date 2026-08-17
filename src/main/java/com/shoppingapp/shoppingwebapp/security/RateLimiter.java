package com.shoppingapp.shoppingwebapp.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window counter, in memory.
 *
 * <p><b>Its limits, stated plainly.</b> The counters live in this process, so
 * they reset on restart and are not shared between instances -- running two
 * copies of the app doubles every limit. And a fixed window lets a caller
 * spend a whole window's allowance at the end of one window and again at the
 * start of the next, so the true worst case over a short span is twice the
 * limit.
 *
 * <p>Both are acceptable here and neither is acceptable forever. This exists
 * because the alternative was no limit at all, which let anyone brute-force a
 * password or drain the mail quota. Redis, or a sliding window, is where this
 * goes when there is more than one instance.
 */
@Component
public class RateLimiter {

    /** Beyond this many tracked keys, expired ones are swept before adding more. */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** How many attempts are allowed, and over what span. */
    public record Policy(int limit, Duration window) {
    }

    /**
     * Records an attempt and says whether it is permitted.
     *
     * @return true when the caller is within its allowance
     */
    public boolean tryAcquire(String key, Policy policy) {
        Instant now = Instant.now();
        if (windows.size() > PRUNE_THRESHOLD) {
            prune(now);
        }
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.hasExpired(now, policy.window())) {
                return new Window(now);
            }
            return existing;
        });
        return window.count().incrementAndGet() <= policy.limit();
    }

    /** How long until this key's window rolls over. Never negative. */
    public Duration retryAfter(String key, Policy policy) {
        Window window = windows.get(key);
        if (window == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(window.startedAt(), Instant.now());
        Duration remaining = policy.window().minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Forgets a key. Called when an attempt succeeds, so a customer who signs
     * in correctly is not still carrying their earlier typos.
     */
    public void reset(String key) {
        windows.remove(key);
    }

    /**
     * Forgets every key.
     *
     * <p>Operationally this is the "let everyone back in" lever for when a
     * limit turns out to be too tight and people are stuck. Tests use it to
     * stop one class's attempts counting against another's, since the counters
     * are a singleton and outlive any single test method.
     */
    public void resetAll() {
        windows.clear();
    }

    /** How many attempts this key has made in its current window. */
    public int attempts(String key) {
        Window window = windows.get(key);
        return window == null ? 0 : window.count().get();
    }

    private void prune(Instant now) {
        // A generous cutoff: anything untouched for an hour cannot still be
        // inside any window this app uses.
        windows.entrySet().removeIf(entry ->
                entry.getValue().hasExpired(now, Duration.ofHours(1)));
    }

    private record Window(Instant startedAt, AtomicInteger count) {

        Window(Instant startedAt) {
            this(startedAt, new AtomicInteger());
        }

        boolean hasExpired(Instant now, Duration length) {
            return Duration.between(startedAt, now).compareTo(length) >= 0;
        }
    }
}
