package com.shoppingapp.shoppingwebapp.model;

/**
 * Product categories for a domestic solar upgrade catalogue.
 */
public enum Category {

    PANEL("Solar Panels"),
    INVERTER("Inverters"),
    BATTERY("Battery Storage"),
    MOUNTING("Mounting & Racking"),
    EV_CHARGER("EV Chargers"),
    MONITORING("Monitoring");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
