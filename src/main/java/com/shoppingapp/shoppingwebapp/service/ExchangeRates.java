package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Money;
import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts the naira totals into the currency a given payment method can
 * actually charge.
 *
 * <p>Card and bank transfer settle through a Nigerian provider, so they charge
 * the naira amount unchanged. PayPal has no naira support and the receiving
 * account is German, so PayPal orders are charged in euro.
 *
 * <p>The rate is a configured constant, not a live feed. That is a deliberate
 * limitation and it is visible to the customer: a hand-set rate that is a week
 * stale quietly changes what the shop earns per sale. Replacing this with a
 * rates API means changing {@link #nairaPerEuro()} and nothing else, because
 * every caller already snapshots the rate it was given rather than re-reading
 * it later.
 */
@Service
public class ExchangeRates {

    private final BigDecimal nairaPerEuro;

    public ExchangeRates(@Value("${app.exchange.naira-per-euro:1800}") BigDecimal nairaPerEuro) {
        if (nairaPerEuro.signum() <= 0) {
            throw new IllegalArgumentException("app.exchange.naira-per-euro must be positive");
        }
        this.nairaPerEuro = nairaPerEuro;
    }

    public BigDecimal nairaPerEuro() {
        return nairaPerEuro;
    }

    /** The currency the given method will actually be charged in. */
    public String currencyFor(PaymentMethod method) {
        return method == PaymentMethod.PAYPAL ? "EUR" : Money.BASE_CURRENCY;
    }

    /**
     * The amount to charge, in that method's currency.
     *
     * <p>Rounded half-up to two places. The customer is shown this exact figure
     * before placing the order and it is stored on the order, so what they were
     * quoted is what the provider is asked for even if the rate moves.
     */
    public BigDecimal convert(BigDecimal naira, String targetCurrency) {
        if (Money.BASE_CURRENCY.equals(targetCurrency)) {
            return naira.setScale(2, RoundingMode.HALF_UP);
        }
        if (!"EUR".equals(targetCurrency)) {
            throw new IllegalArgumentException("No rate configured for " + targetCurrency);
        }
        return naira.divide(nairaPerEuro, 2, RoundingMode.HALF_UP);
    }

    /**
     * Naira per unit of the target currency, or null when no conversion
     * happened. Stored on the order so the arithmetic can be checked later.
     */
    public BigDecimal rateFor(String targetCurrency) {
        return Money.BASE_CURRENCY.equals(targetCurrency) ? null : nairaPerEuro;
    }
}
