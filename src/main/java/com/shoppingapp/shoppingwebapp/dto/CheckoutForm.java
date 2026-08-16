package com.shoppingapp.shoppingwebapp.dto;

import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CheckoutForm {

    @NotBlank(message = "Please enter the delivery name")
    private String shippingName = "";

    @NotBlank(message = "Please enter the delivery address")
    private String shippingAddress = "";

    @NotBlank(message = "Please enter the postcode")
    private String shippingPostcode = "";

    /**
     * Deliberately has no default: a payment method silently pre-selected for
     * the customer is a choice they did not make.
     */
    @NotNull(message = "Please choose how you would like to pay")
    private PaymentMethod paymentMethod;

    public String getShippingName() {
        return shippingName;
    }

    public void setShippingName(String shippingName) {
        this.shippingName = shippingName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getShippingPostcode() {
        return shippingPostcode;
    }

    public void setShippingPostcode(String shippingPostcode) {
        this.shippingPostcode = shippingPostcode;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
