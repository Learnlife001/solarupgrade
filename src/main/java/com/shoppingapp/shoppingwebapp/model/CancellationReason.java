package com.shoppingapp.shoppingwebapp.model;

/**
 * Why an unpaid order stopped being an order.
 *
 * <p>The mechanics are identical whoever does it -- the stock goes back, the
 * status changes -- but what the customer should be told is not. "This lapsed
 * because you never paid" sent to somebody who pressed Cancel a minute earlier
 * reads as though the shop was not listening.
 */
public enum CancellationReason {

    /** The expiry job: nobody asked, the window simply ran out. */
    EXPIRED,

    /** Somebody in the back office cancelled it. */
    ADMIN,

    /** The customer changed their mind and said so. */
    CUSTOMER
}
