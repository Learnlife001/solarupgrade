package com.shoppingapp.shoppingwebapp.model;

/**
 * Whether a German supplier will ship to Nigeria.
 *
 * <p>The single most valuable field in the directory. Most German wholesalers
 * sell inside the EU only -- VAT reclaim, freight and cross-border warranty
 * claims make export a nuisance they decline -- so "who actually exports" is
 * the thing no public list tells you, and the reason this directory is worth
 * reading at all.
 *
 * <p>UNKNOWN is a real answer, not a gap to be filled with a guess. A directory
 * that quietly promotes "we have not asked" to "yes" sends someone into a
 * pointless negotiation.
 */
public enum ExportStance {

    YES("Exports to Nigeria", "Has confirmed they ship to Nigeria."),
    ON_REQUEST("On request", "Will consider it, usually depending on order size."),
    NO("EU only", "Sells inside the EU only. Listed so you do not waste the email."),
    UNKNOWN("Not yet asked", "We have not confirmed this either way.");

    private final String displayName;
    private final String explanation;

    ExportStance(String displayName, String explanation) {
        this.displayName = displayName;
        this.explanation = explanation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExplanation() {
        return explanation;
    }
}
