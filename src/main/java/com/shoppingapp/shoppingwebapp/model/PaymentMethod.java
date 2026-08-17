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
 *
 * <p>Stored as a name, not an ordinal (see {@code Order.paymentMethod}), so
 * these constants can be reordered or regrouped without touching stored rows.
 */
public enum PaymentMethod {

    PAYPAL("PayPal",
            "You will be sent to PayPal to approve the payment, then brought back here",
            Availability.OFFERED),

    /*
     * Waiting on OPay. Both were offered while nothing could actually charge
     * for them, which left the order page's pay button standing in for a
     * provider -- and that button belongs to the buyer. They come back the day
     * OPay can confirm a payment, not before.
     */
    CARD("Debit or credit card",
            "Visa, Mastercard and Verve, entered on our payment provider's secure page",
            Availability.COMING_SOON),
    BANK_TRANSFER("Bank transfer",
            "Pay from any Nigerian bank account or with your bank's USSD code",
            Availability.COMING_SOON),

    /*
     * Withdrawn. These are kept only so that orders already placed with them
     * still load -- removing the constant would make Hibernate throw on the
     * historical row rather than simply render an old order.
     */
    APPLE_PAY("Apple Pay", "No longer offered", Availability.WITHDRAWN),
    SEPA("SEPA Direct Debit", "No longer offered", Availability.WITHDRAWN),
    KLARNA("Klarna", "No longer offered", Availability.WITHDRAWN);

    /**
     * Why a method is or is not on the checkout page. "Not offered" was one
     * flag until card and transfer were pulled, and it hid a real difference:
     * Klarna is gone, card is coming back. The customer is told the second
     * thing and not the first.
     */
    public enum Availability {
        OFFERED,
        COMING_SOON,
        WITHDRAWN
    }

    private final String displayName;
    private final String description;
    private final Availability availability;

    PaymentMethod(String displayName, String description, Availability availability) {
        this.displayName = displayName;
        this.description = description;
        this.availability = availability;
    }

    /** The methods a customer may choose today, in the order they are shown. */
    public static List<PaymentMethod> offered() {
        return Arrays.stream(values()).filter(PaymentMethod::isOffered).toList();
    }

    /** Named on the checkout page so nobody wonders where card payment went. */
    public static List<PaymentMethod> comingSoon() {
        return Arrays.stream(values())
                .filter(method -> method.availability == Availability.COMING_SOON)
                .toList();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Availability getAvailability() {
        return availability;
    }

    public boolean isOffered() {
        return availability == Availability.OFFERED;
    }
}
