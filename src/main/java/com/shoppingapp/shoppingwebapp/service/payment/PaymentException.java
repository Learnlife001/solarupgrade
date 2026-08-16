package com.shoppingapp.shoppingwebapp.service.payment;

/**
 * Something went wrong talking to a payment provider.
 *
 * <p>Always means "we do not know that money moved", so callers must leave the
 * order unpaid rather than guessing.
 */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
