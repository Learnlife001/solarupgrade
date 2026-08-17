package com.shoppingapp.shoppingwebapp.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private static final String EMAIL = "chigozie@example.com";

    @Test
    void aLongOrdinaryPassphraseIsAccepted() {
        // Length and unpredictability, no symbol soup required.
        assertThat(PasswordPolicy.reject("correct horse battery", EMAIL)).isNull();
        assertThat(PasswordPolicy.reject("sunny-rooftop-42", EMAIL)).isNull();
    }

    @Test
    void tooShortIsRejectedEvenWhenItLooksComplicated() {
        assertThat(PasswordPolicy.reject("Ab3$xY!", EMAIL)).contains("at least");
    }

    /** The whole reason this class exists. */
    @Test
    void theObviousChoicesAreRejected() {
        assertThat(PasswordPolicy.reject("password123", EMAIL)).isNotNull();
        assertThat(PasswordPolicy.reject("Password123", EMAIL)).isNotNull();
        assertThat(PasswordPolicy.reject("1234567890", EMAIL)).isNotNull();
        assertThat(PasswordPolicy.reject("welcome123", EMAIL)).isNotNull();
    }

    /**
     * A password built from the address it protects is public knowledge -- the
     * attacker already has the email, that being how they found the account.
     */
    @Test
    void aPasswordContainingTheEmailIsRejected() {
        assertThat(PasswordPolicy.reject("chigozie-secret", EMAIL))
                .contains("does not contain your email");
        assertThat(PasswordPolicy.reject("myCHIGOZIEpass", EMAIL))
                .contains("does not contain your email");
    }

    @Test
    void oneCharacterRepeatedIsRejectedHoweverLong() {
        assertThat(PasswordPolicy.reject("aaaaaaaaaaaaaaaa", EMAIL)).isNotNull();
    }

    @Test
    void blankAndNullAreRejectedWithoutThrowing() {
        assertThat(PasswordPolicy.reject(null, EMAIL)).isNotNull();
        assertThat(PasswordPolicy.reject("   ", EMAIL)).isNotNull();
        // A missing email must not stop the rest of the policy applying.
        assertThat(PasswordPolicy.reject("password123", null)).isNotNull();
        assertThat(PasswordPolicy.reject("a reasonable passphrase", null)).isNull();
    }
}
