package com.shoppingapp.shoppingwebapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The details a customer is entitled to know before they hand over money.
 *
 * <p>Configuration rather than template text, because they belong to the
 * business rather than to the code: they change when the company moves or
 * changes phone number, and that should not be a deploy of new markup.
 *
 * <p>They start empty on purpose. Legal pages built around invented details
 * would be worse than none at all -- a customer relies on a returns address to
 * exercise a right, and one that does not exist takes the right with it. While
 * anything here is blank the pages carry a visible draft notice, so an
 * incomplete page cannot quietly pass for a finished one.
 */
@Component
@ConfigurationProperties(prefix = "app.business")
public class BusinessDetails {

    /** Registered company name, if it differs from the trading name. */
    private String legalName = "";

    /** RC number from the Corporate Affairs Commission. */
    private String registrationNumber = "";

    private String address = "";
    private String phone = "";
    private String supportEmail = "";

    /** Days a customer has to change their mind. */
    private int returnsWindowDays = 7;

    /** Working days from dispatch to delivery, as quoted on the site. */
    private String deliveryEstimate = "5 to 10 working days";

    /**
     * True once every detail a customer might need has been supplied.
     *
     * <p>The returns window and delivery estimate are not checked: both have
     * defaults that are true statements about how the shop already works.
     */
    public boolean isComplete() {
        return notBlank(legalName)
                && notBlank(registrationNumber)
                && notBlank(address)
                && notBlank(phone)
                && notBlank(supportEmail);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** What a page should print where a missing detail would go. */
    public String getLegalNameOrPlaceholder() {
        return notBlank(legalName) ? legalName : "[registered company name]";
    }

    public String getRegistrationNumberOrPlaceholder() {
        return notBlank(registrationNumber) ? registrationNumber : "[RC number]";
    }

    public String getAddressOrPlaceholder() {
        return notBlank(address) ? address : "[registered address]";
    }

    public String getPhoneOrPlaceholder() {
        return notBlank(phone) ? phone : "[telephone number]";
    }

    public String getSupportEmailOrPlaceholder() {
        return notBlank(supportEmail) ? supportEmail : "[support email address]";
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    public int getReturnsWindowDays() {
        return returnsWindowDays;
    }

    public void setReturnsWindowDays(int returnsWindowDays) {
        this.returnsWindowDays = returnsWindowDays;
    }

    public String getDeliveryEstimate() {
        return deliveryEstimate;
    }

    public void setDeliveryEstimate(String deliveryEstimate) {
        this.deliveryEstimate = deliveryEstimate;
    }
}
