package com.shoppingapp.shoppingwebapp.model;

import java.util.Arrays;
import java.util.List;

/**
 * How the customer chose to pay.
 *
 * <p>Recording the choice is the useful half of a payment integration: it is
 * what a provider needs to be told, and what support needs to see afterwards.
 * Nothing here captures card numbers or bank details -- that only happens on
 * the provider's own hosted page, never in these forms.
 */
public enum PaymentMethod {

    CARD("Debit or credit card", "Visa, Mastercard and Verve, entered on our payment provider's secure page", true),
    PAYPAL("PayPal", "You will be sent to PayPal to approve the payment, then brought back here", true),
    BANK_TRANSFER("Bank transfer", "Pay from any Nigerian bank account or with your bank's USSD code", true),

    /*
     * Withdrawn. These are kept only so that orders already placed with them
     * still load -- removing the constant would make Hibernate throw on the
     * historical row rather than simply render an old order. They are not
     * offered at checkout.
     */
    APPLE_PAY("Apple Pay", "No longer offered", false),
    SEPA("SEPA Direct Debit", "No longer offered", false),
    KLARNA("Klarna", "No longer offered", false);

    private final String displayName;
    private final String description;
    private final boolean offered;

    PaymentMethod(String displayName, String description, boolean offered) {
        this.displayName = displayName;
        this.description = description;
        this.offered = offered;
    }

    /** The methods a customer may choose today, in the order they are shown. */
    public static List<PaymentMethod> offered() {
        return Arrays.stream(values()).filter(PaymentMethod::isOffered).toList();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOffered() {
        return offered;
    }
}
