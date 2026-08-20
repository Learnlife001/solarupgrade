package com.shoppingapp.shoppingwebapp.service.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether an error is worth an email yet.
 *
 * <p>Alerting on every exception is how alerting gets switched off. One broken
 * page that a crawler hits fifty times a minute would send fifty emails, and
 * after the first morning of that nobody reads any of them -- so the next real
 * outage goes unnoticed for exactly the reason the alerts existed.
 *
 * <p>Two limits, doing different jobs:
 *
 * <dl>
 *   <dt>Per-signature cooldown</dt>
 *   <dd>The same fault stays quiet for a while after its first report. You
 *       already know; a second email adds nothing.</dd>
 *   <dt>Hourly cap across everything</dt>
 *   <dd>Protects against the case the cooldown cannot: many <em>different</em>
 *       faults at once, which is what a bad deploy looks like. The cap is also
 *       what stops a burst from eating a month of a free mail quota.</dd>
 * </dl>
 *
 * <p>Nothing is lost while quiet: suppressed occurrences are counted and the
 * next email that does go out says how many there were, so the shape of the
 * problem survives even when the messages do not.
 *
 * <p>Deliberately in memory. Restarting resets it, which is the right
 * behaviour: a fresh process is exactly when you want to hear about the first
 * error again.
 */
public class AlertBudget {

    /** How long one signature stays quiet after being reported. */
    private final Duration cooldown;

    /** Most emails in any rolling hour, across every signature. */
    private final int maxPerHour;

    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressed = new ConcurrentHashMap<>();
    private final Deque<Instant> sentTimes = new ArrayDeque<>();

    public AlertBudget(Duration cooldown, int maxPerHour) {
        this.cooldown = cooldown;
        this.maxPerHour = maxPerHour;
    }

    /**
     * @return how many occurrences this email covers, or empty when the error
     *         should stay quiet for now
     */
    public synchronized Decision record(String signature, Instant now) {
        Instant last = lastSent.get(signature);
        if (last != null && last.plus(cooldown).isAfter(now)) {
            suppressed.merge(signature, 1, Integer::sum);
            return Decision.quiet();
        }

        // Drop anything that fell out of the rolling window before counting.
        while (!sentTimes.isEmpty() && sentTimes.peekFirst().plus(Duration.ofHours(1)).isBefore(now)) {
            sentTimes.pollFirst();
        }
        if (sentTimes.size() >= maxPerHour) {
            suppressed.merge(signature, 1, Integer::sum);
            return Decision.quiet();
        }

        sentTimes.addLast(now);
        lastSent.put(signature, now);
        // Taken and cleared in one step: whatever went unreported while this
        // signature was quiet is what the email about to go out will mention.
        Integer alsoSeen = suppressed.remove(signature);
        return Decision.send(alsoSeen == null ? 0 : alsoSeen);
    }

    /** What to do about one occurrence. */
    public record Decision(boolean send, int alsoSuppressed) {

        static Decision quiet() {
            return new Decision(false, 0);
        }

        static Decision send(int alsoSuppressed) {
            return new Decision(true, alsoSuppressed);
        }
    }
}
