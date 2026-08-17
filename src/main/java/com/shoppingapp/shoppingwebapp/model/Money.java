package com.shoppingapp.shoppingwebapp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

/**
 * Formatting for the two currencies this shop quotes in.
 *
 * <p>The store's base currency is the naira: that is what the catalogue is
 * priced in, what the totals are computed in, and what OPay settles. The euro
 * appears only where PayPal is involved, because PayPal has no naira support
 * and the receiving account is a German one, so euro lands there natively
 * rather than being converted twice.
 *
 * <p>Formatting is done here rather than with a JDK currency formatter because
 * the JDK's output depends on the server's default locale -- the same amount
 * would render differently on a developer's laptop and on the deployed host.
 */
public final class Money {

    public static final String BASE_CURRENCY = "NGN";

    private static final Map<String, String> SYMBOLS = Map.of(
            "NGN", "₦",   // naira
            "EUR", "€",   // euro
            "GBP", "£",
            "USD", "$");

    private Money() {
    }

    /** e.g. {@code ₦378,000.00}. */
    public static String format(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "";
        }
        String code = (currencyCode == null || currencyCode.isBlank()) ? BASE_CURRENCY : currencyCode;
        return symbol(code) + grouped(amount);
    }

    public static String base(BigDecimal amount) {
        return format(amount, BASE_CURRENCY);
    }

    public static String symbol(String currencyCode) {
        return SYMBOLS.getOrDefault(currencyCode, currencyCode + " ");
    }

    /**
     * Decimals only when there are any.
     *
     * <p>{@code ₦8,300,000.00} is how a spreadsheet writes it, not how a shop
     * does -- and on seven-figure naira the trailing zeros are pure noise.
     * Euro amounts still show their cents, because those are rarely whole.
     */
    private static String grouped(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        boolean whole = rounded.remainder(BigDecimal.ONE).signum() == 0;
        // Locale.ROOT pins the separators, so output does not shift with the
        // host's locale.
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00",
                new DecimalFormatSymbols(Locale.ROOT));
        return format.format(rounded);
    }
}
