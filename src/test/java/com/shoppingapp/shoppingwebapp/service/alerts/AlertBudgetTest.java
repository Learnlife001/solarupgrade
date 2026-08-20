package com.shoppingapp.shoppingwebapp.service.alerts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules that keep alerting worth having.
 *
 * <p>Time is passed in rather than read from the clock, so these run in
 * milliseconds and say exactly which instant they mean. A cooldown test that
 * sleeps for thirty minutes is a test nobody runs.
 */
class AlertBudgetTest {

    private static final Instant NOON = Instant.parse("2026-08-20T12:00:00Z");

    private final AlertBudget budget = new AlertBudget(Duration.ofMinutes(30), 3);

    @Test
    void theFirstOccurrenceIsAlwaysReported() {
        assertThat(budget.record("a", NOON).send()).isTrue();
    }

    @Test
    void theSameFaultStaysQuietDuringTheCooldown() {
        budget.record("a", NOON);

        assertThat(budget.record("a", NOON.plus(Duration.ofMinutes(29))).send()).isFalse();
    }

    @Test
    void theSameFaultIsReportedAgainOnceTheCooldownHasPassed() {
        budget.record("a", NOON);

        assertThat(budget.record("a", NOON.plus(Duration.ofMinutes(31))).send()).isTrue();
    }

    /** Silence must not lose the count, or a storm looks like a single blip. */
    @Test
    void theNextReportSaysHowManyWentUnreported() {
        budget.record("a", NOON);
        budget.record("a", NOON.plus(Duration.ofMinutes(1)));
        budget.record("a", NOON.plus(Duration.ofMinutes(2)));

        AlertBudget.Decision next = budget.record("a", NOON.plus(Duration.ofMinutes(31)));

        assertThat(next.send()).isTrue();
        assertThat(next.alsoSuppressed()).isEqualTo(2);
    }

    /** And the count resets, so the following email does not claim them twice. */
    @Test
    void theSuppressedCountIsClearedOnceReported() {
        budget.record("a", NOON);
        budget.record("a", NOON.plus(Duration.ofMinutes(1)));
        budget.record("a", NOON.plus(Duration.ofMinutes(31)));

        AlertBudget.Decision later = budget.record("a", NOON.plus(Duration.ofMinutes(62)));

        assertThat(later.alsoSuppressed()).isZero();
    }

    /**
     * A different fault is a different cooldown. One broken page must not
     * silence the report of a second one breaking.
     */
    @Test
    void differentFaultsHaveTheirOwnCooldowns() {
        budget.record("a", NOON);

        assertThat(budget.record("b", NOON.plusSeconds(1)).send()).isTrue();
    }

    /**
     * The cap is what the per-fault cooldown cannot do: a bad deploy breaks
     * many different things at once, and each one would otherwise be a fresh
     * signature with a fresh right to email.
     */
    @Test
    void theHourlyCapHoldsAcrossDifferentFaults() {
        assertThat(budget.record("a", NOON).send()).isTrue();
        assertThat(budget.record("b", NOON).send()).isTrue();
        assertThat(budget.record("c", NOON).send()).isTrue();

        assertThat(budget.record("d", NOON).send()).isFalse();
    }

    @Test
    void theCapIsARollingHourNotACalendarOne() {
        budget.record("a", NOON);
        budget.record("b", NOON);
        budget.record("c", NOON);

        assertThat(budget.record("d", NOON.plus(Duration.ofMinutes(61))).send()).isTrue();
    }

    /** Anything held back by the cap is still counted, like a cooldown. */
    @Test
    void thingsHeldBackByTheCapAreCountedToo() {
        budget.record("a", NOON);
        budget.record("b", NOON);
        budget.record("c", NOON);
        budget.record("d", NOON);
        budget.record("d", NOON);

        AlertBudget.Decision later = budget.record("d", NOON.plus(Duration.ofMinutes(61)));

        assertThat(later.send()).isTrue();
        assertThat(later.alsoSuppressed()).isEqualTo(2);
    }
}
