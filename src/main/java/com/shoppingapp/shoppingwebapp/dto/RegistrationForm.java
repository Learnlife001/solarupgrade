package com.shoppingapp.shoppingwebapp.dto;

import jakarta.validation.constraints.Email;
import com.shoppingapp.shoppingwebapp.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Please enter your name")
    private String fullName = "";

    @NotBlank(message = "Please enter your email")
    @Email(message = "That does not look like a valid email address")
    private String email = "";

    /*
     * The length floor lives here so the field shows an error like any other.
     * Everything else about the password -- blocklist, not containing the email
     * -- is in PasswordPolicy and applied by the controller, because those
     * checks need the other fields to make sense.
     */
    @NotBlank(message = "Please choose a password")
    @Size(min = PasswordPolicy.MIN_LENGTH,
            message = "Please use at least " + PasswordPolicy.MIN_LENGTH + " characters")
    private String password = "";

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword = "";

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
