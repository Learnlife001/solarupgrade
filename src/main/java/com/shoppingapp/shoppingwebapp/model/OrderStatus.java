package com.shoppingapp.shoppingwebapp.model;

public enum OrderStatus {

    /** Order recorded, payment not yet captured. */
    PENDING_PAYMENT("Pending payment"),
    PAID("Paid"),
    SHIPPED("Shipped"),
    CANCELLED("Cancelled");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
