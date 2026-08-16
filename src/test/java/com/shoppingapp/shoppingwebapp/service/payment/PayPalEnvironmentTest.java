package com.shoppingapp.shoppingwebapp.service.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A misconfigured environment must stop the app, not quietly pick one.
 *
 * <p>This exists because a real deployment had app.paypal.env set to the
 * PayPal app's name. The old code treated anything that was not "live" as
 * sandbox, so it worked by accident -- and the same leniency would have sent a
 * production site to the test PayPal, where no money moves and no order ever
 * settles, with nothing anywhere saying so.
 */
class PayPalEnvironmentTest {

    @Test
    void sandboxAndLiveResolveToTheirOwnHosts() {
        assertThat(PayPalClient.baseUrlFor("sandbox")).isEqualTo("https://api-m.sandbox.paypal.com");
        assertThat(PayPalClient.baseUrlFor("live")).isEqualTo("https://api-m.paypal.com");
    }

    @Test
    void caseAndSurroundingSpaceDoNotMatter() {
        assertThat(PayPalClient.baseUrlFor("  LIVE ")).isEqualTo("https://api-m.paypal.com");
        assertThat(PayPalClient.baseUrlFor("Sandbox")).isEqualTo("https://api-m.sandbox.paypal.com");
        // "production" is the word people reach for when "live" does not occur
        // to them, and meaning it is unambiguous.
        assertThat(PayPalClient.baseUrlFor("production")).isEqualTo("https://api-m.paypal.com");
    }

    @Test
    void anythingElseIsRefusedRatherThanGuessed() {
        for (String bad : new String[]{"Default Application", "", "  ", "prod", "test", "livee", null}) {
            assertThatThrownBy(() -> PayPalClient.baseUrlFor(bad))
                    .as("env %s", bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be 'sandbox' or 'live'");
        }
    }
}
