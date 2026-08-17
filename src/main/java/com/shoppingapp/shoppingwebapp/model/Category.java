package com.shoppingapp.shoppingwebapp.model;

import java.util.List;

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

    /**
     * The categories a buyer of this one usually needs next.
     *
     * <p>Held here rather than as product-to-product rows because a solar
     * install is a system: panels need an inverter and something to bolt them
     * to, an inverter without storage is a daytime-only system, and a battery
     * is useless without something charging it. That relationship is between
     * kinds of thing, not between two particular SKUs, so it does not change
     * when the catalogue does and needs no schema.
     */
    public List<Category> pairsWith() {
        return switch (this) {
            case PANEL -> List.of(INVERTER, MOUNTING);
            case INVERTER -> List.of(BATTERY, PANEL);
            case BATTERY -> List.of(INVERTER, MONITORING);
            case MOUNTING -> List.of(PANEL, INVERTER);
            case EV_CHARGER -> List.of(MONITORING, INVERTER);
            case MONITORING -> List.of(BATTERY, EV_CHARGER);
        };
    }
}
