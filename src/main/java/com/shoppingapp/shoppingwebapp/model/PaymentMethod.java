package com.shoppingapp.shoppingwebapp.model;

/**
 * How the customer chose to pay.
 *
 * <p>Recording the choice is the useful half of a payment integration: it is
 * what a provider needs to be told, and what support needs to see afterwards.
 * Nothing here captures card numbers or bank details -- that only happens on
 * the provider's own hosted page or embedded element, never in these forms.
 */
public enum PaymentMethod {

    CARD("Debit or credit card", "Visa, Mastercard, American Express"),
    PAYPAL("PayPal", "Pay with your PayPal balance or linked account"),
    APPLE_PAY("Apple Pay", "Confirm with Face ID or Touch ID"),
    SEPA("SEPA Direct Debit", "Pay from a euro bank account"),
    KLARNA("Klarna", "Pay later or in instalments");

    private final String displayName;
    private final String description;

    PaymentMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
