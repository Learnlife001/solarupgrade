package com.shoppingapp.shoppingwebapp.security;

import java.util.Locale;
import java.util.Set;

/**
 * What counts as an acceptable password.
 *
 * <p>Length and a blocklist, not composition rules. Requiring a capital, a
 * digit and a symbol is the familiar approach and it reliably produces
 * {@code Password1!} -- it pushes people towards the small corner of the
 * keyspace attackers try first, and towards writing the result down. Modern
 * guidance (NIST SP 800-63B) is the opposite: insist on length, refuse known
 * bad choices, and otherwise stay out of the way.
 *
 * <p>The blocklist here is deliberately short. A real one is a file of the
 * several thousand most-breached passwords; this catches the handful that
 * someone building a demo actually types, and the shape of the check is what
 * matters -- swapping in a bigger list changes one constant.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    private static final Set<String> BLOCKED = Set.of(
            "password", "password1", "password12", "password123", "password1234",
            "passw0rd", "qwertyuiop", "1234567890", "12345678901", "letmein123",
            "iloveyou1", "admin12345", "welcome123", "solarupgrade", "changeme123",
            "abc12345678", "qwerty12345", "trustno1234", "monkey12345", "football123");

    private PasswordPolicy() {
    }

    /**
     * @return null when the password is acceptable, otherwise the reason to
     *         show the person who typed it
     */
    public static String reject(String password, String email) {
        if (password == null || password.isBlank()) {
            return "Please choose a password";
        }
        if (password.length() < MIN_LENGTH) {
            return "Please use at least " + MIN_LENGTH + " characters — length matters more than symbols";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(lower)) {
            return "That password is one of the first any attacker tries. Please pick another";
        }
        // A password built from the address it protects is public knowledge.
        String localPart = localPartOf(email);
        if (localPart != null && localPart.length() >= 3 && lower.contains(localPart)) {
            return "Please choose a password that does not contain your email address";
        }
        if (isOneRepeatedCharacter(lower)) {
            return "Please use more than one repeated character";
        }
        return null;
    }

    private static String localPartOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        String local = (at > 0 ? email.substring(0, at) : email).trim().toLowerCase(Locale.ROOT);
        return local.isBlank() ? null : local;
    }

    private static boolean isOneRepeatedCharacter(String value) {
        return value.chars().distinct().count() == 1;
    }
}
