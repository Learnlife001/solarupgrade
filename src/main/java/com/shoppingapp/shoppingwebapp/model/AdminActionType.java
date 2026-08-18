package com.shoppingapp.shoppingwebapp.model;

/**
 * What an administrator can do that is worth remembering.
 *
 * <p>Stored as a name rather than an ordinal, so a constant can be added or
 * moved without rewriting what past actions mean.
 */
public enum AdminActionType {

    ORDER_SHIPPED("Marked as shipped"),
    ORDER_CANCELLED("Cancelled and stock returned"),
    STOCK_SET("Stock level set");

    private final String displayName;

    AdminActionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
