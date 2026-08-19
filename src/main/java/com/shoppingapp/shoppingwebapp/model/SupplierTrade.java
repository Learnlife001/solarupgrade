package com.shoppingapp.shoppingwebapp.model;

/** Whether a supplier sells single units, pallets, or both. */
public enum SupplierTrade {

    RETAIL("Retail", "Will sell single units."),
    WHOLESALE("Wholesale", "Pallet or container quantities only."),
    BOTH("Retail and wholesale", "Will sell either way.");

    private final String displayName;
    private final String explanation;

    SupplierTrade(String displayName, String explanation) {
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
