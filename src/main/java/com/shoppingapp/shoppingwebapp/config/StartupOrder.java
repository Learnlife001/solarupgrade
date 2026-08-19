package com.shoppingapp.shoppingwebapp.config;

/**
 * The order the startup runners have to run in.
 *
 * <p>Numbers in one place because the constraint is a relationship between
 * runners, not a property of either: anything that <em>reads</em> accounts at
 * startup must run after everything that <em>writes</em> them. Leaving both
 * unordered looked fine and was arbitrary -- Spring treats an unordered runner
 * as lowest precedence, so two of them tie and the winner is whichever bean
 * happened to be registered first.
 *
 * <p>Gaps between the values so a runner can be slotted in later without
 * renumbering the rest.
 */
final class StartupOrder {

    /** Creates the demo catalogue and demo account. */
    static final int SEED_DEMO_DATA = 100;

    /** Promotes the configured addresses, so it must see any account just seeded. */
    static final int GRANT_ADMIN_ROLE = 200;

    private StartupOrder() {
    }
}
