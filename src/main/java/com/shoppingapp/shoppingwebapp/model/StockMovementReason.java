package com.shoppingapp.shoppingwebapp.model;

/**
 * Why a stock figure changed.
 *
 * <p>Stored as a name rather than an ordinal, so a constant can be added or
 * moved without rewriting what past movements mean.
 */
public enum StockMovementReason {

    SALE("Sold"),
    CANCELLATION("Order cancelled"),
    REFUND("Refunded before dispatch"),
    STOCK_TAKE("Counted in the back office");

    private final String displayName;

    StockMovementReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
