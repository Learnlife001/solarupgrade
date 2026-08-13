package com.shoppingapp.shoppingwebapp.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckoutForm {

    @NotBlank(message = "Please enter the delivery name")
    private String shippingName = "";

    @NotBlank(message = "Please enter the delivery address")
    private String shippingAddress = "";

    @NotBlank(message = "Please enter the postcode")
    private String shippingPostcode = "";

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
}
