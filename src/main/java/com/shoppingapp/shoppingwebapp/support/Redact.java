package com.shoppingapp.shoppingwebapp.support;

/**
 * Keeps customer identifiers out of the logs.
 *
 * <p>Log lines outlive the request that wrote them: on Render they sit in a
 * retained stream, they are read by anyone with dashboard access, and they end
 * up in whatever aggregator gets added later. A full address in there turns
 * "we hold customer data in one encrypted database" into something less true,
 * for no operational gain — a masked address answers every question a log line
 * is actually asked, including which provider is bouncing.
 */
public final class Redact {

    private Redact() {
    }

    /**
     * {@code gregcj6@gmail.com} becomes {@code g***@gmail.com}.
     *
     * <p>The domain survives on purpose. "Every send to one provider is
     * failing" is a real thing to need from a log, and the domain is not what
     * identifies the person.
     */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "(no address)";
        }
        int at = email.indexOf('@');
        if (at < 1) {
            // Not shaped like an address; say nothing rather than guess which
            // part of it was safe to print.
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
