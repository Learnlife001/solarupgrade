package com.shoppingapp.shoppingwebapp.dto;

import com.shoppingapp.shoppingwebapp.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CheckoutForm {

    @NotBlank(message = "Please enter the delivery name")
    private String shippingName = "";

    @NotBlank(message = "Please enter the street address")
    private String shippingLine1 = "";

    /** Flat, estate, landmark -- genuinely optional. */
    private String shippingLine2 = "";

    @NotBlank(message = "Please enter the city or town")
    private String shippingCity = "";

    @NotBlank(message = "Please enter the state or region")
    private String shippingState = "";

    /**
     * Not required. Most Nigerian addresses carry no postcode in daily use,
     * and demanding one would block a correct address.
     */
    private String shippingPostcode = "";

    @NotBlank(message = "Please choose a country")
    @Size(min = 2, max = 2)
    private String shippingCountry = "NG";

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

    public String getShippingLine1() {
        return shippingLine1;
    }

    public void setShippingLine1(String shippingLine1) {
        this.shippingLine1 = shippingLine1;
    }

    public String getShippingLine2() {
        return shippingLine2;
    }

    public void setShippingLine2(String shippingLine2) {
        this.shippingLine2 = shippingLine2;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingState() {
        return shippingState;
    }

    public void setShippingState(String shippingState) {
        this.shippingState = shippingState;
    }

    public String getShippingPostcode() {
        return shippingPostcode;
    }

    public void setShippingPostcode(String shippingPostcode) {
        this.shippingPostcode = shippingPostcode;
    }

    public String getShippingCountry() {
        return shippingCountry;
    }

    public void setShippingCountry(String shippingCountry) {
        this.shippingCountry = shippingCountry;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
