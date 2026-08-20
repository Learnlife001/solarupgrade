package com.shoppingapp.shoppingwebapp.model;

public enum OrderStatus {

    /** Order recorded, payment not yet captured. */
    PENDING_PAYMENT("Pending payment"),
    PAID("Paid"),
    SHIPPED("Shipped"),
    CANCELLED("Cancelled"),

    /**
     * Paid, then paid back. A separate state from CANCELLED, which means an
     * order that lapsed before any money moved: a customer reading "cancelled"
     * on an order they paid for would reasonably wonder where their money is,
     * and the accounts have to tell the two apart as well.
     */
    REFUNDED("Refunded");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
