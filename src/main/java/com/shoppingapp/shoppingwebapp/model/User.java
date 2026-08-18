package com.shoppingapp.shoppingwebapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    /** Wrong guesses allowed against one verification code before it is burned. */
    public static final int MAX_VERIFICATION_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash. Never the raw password. */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Until this is true the account cannot sign in. It is the only thing that
     * makes a typed-in address mean anything: an unverified account proves
     * nothing about whether the mailbox exists or belongs to the registrant.
     */
    @Column(nullable = false)
    private boolean emailVerified = false;

    /** Six-digit code the user types in; cleared once used. */
    @Column(length = 6)
    private String verificationCode;

    @Column
    private Instant verificationCodeExpiresAt;

    /**
     * Failed attempts against the current code. A six-digit code is only a
     * million possibilities, so without a cap it could simply be guessed;
     * exceeding {@link #MAX_VERIFICATION_ATTEMPTS} burns the code and forces a
     * new one to be requested.
     */
    @Column(nullable = false)
    private int verificationAttempts = 0;

    /**
     * SHA-256 of the reset token, never the token itself.
     *
     * <p>Unlike the six-digit verification code, this value is a credential:
     * whoever holds it can take the account. Storing the hash means a leaked
     * database dump contains no usable reset links — the same reasoning that
     * puts passwords through bcrypt, applied to the thing that can replace a
     * password. A fast hash is enough here where bcrypt is not, because the
     * input is 256 bits of randomness rather than something a person chose.
     */
    @Column(length = 64)
    private String resetTokenHash;

    @Column
    private Instant resetTokenExpiresAt;

    protected User() {
        // required by JPA
    }

    public User(String email, String password, String fullName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Instant getVerificationCodeExpiresAt() {
        return verificationCodeExpiresAt;
    }

    public int getVerificationAttempts() {
        return verificationAttempts;
    }

    /** Replaces any outstanding code and gives the user a fresh set of attempts. */
    public void issueVerificationCode(String code, Instant expiresAt) {
        this.verificationCode = code;
        this.verificationCodeExpiresAt = expiresAt;
        this.verificationAttempts = 0;
    }

    public boolean isVerificationCodeValid(Instant now) {
        return verificationCode != null
                && verificationCodeExpiresAt != null
                && now.isBefore(verificationCodeExpiresAt)
                && verificationAttempts < MAX_VERIFICATION_ATTEMPTS;
    }

    /**
     * Counts a wrong guess.
     *
     * @return true when that guess exhausted the allowance, leaving the code
     *         dead until a new one is requested.
     */
    public boolean recordFailedVerification() {
        verificationAttempts++;
        if (verificationAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            clearVerificationCode();
            return true;
        }
        return false;
    }

    /** Marks the address confirmed and burns the code so it is single-use. */
    public void markEmailVerified() {
        this.emailVerified = true;
        clearVerificationCode();
    }

    private void clearVerificationCode() {
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public Instant getResetTokenExpiresAt() {
        return resetTokenExpiresAt;
    }

    /** Replaces any outstanding reset link, so only the newest one works. */
    public void issueResetToken(String tokenHash, Instant expiresAt) {
        this.resetTokenHash = tokenHash;
        this.resetTokenExpiresAt = expiresAt;
    }

    public boolean isResetTokenValid(Instant now) {
        return resetTokenHash != null
                && resetTokenExpiresAt != null
                && now.isBefore(resetTokenExpiresAt);
    }

    /**
     * Sets the new password and burns the token in one step, so a reset link
     * cannot be used twice — including by anyone who read it out of a forwarded
     * email or a shared inbox.
     *
     * <p>Also marks the address verified. Following a link we emailed proves
     * control of the mailbox, which is the entire question verification asks;
     * leaving the account unverified afterwards would mean the customer resets
     * their password successfully and still cannot sign in.
     */
    public void applyPasswordReset(String encodedPassword) {
        this.password = encodedPassword;
        this.resetTokenHash = null;
        this.resetTokenExpiresAt = null;
        this.emailVerified = true;
        clearVerificationCode();
    }

    public Long getId() {
        return id;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
