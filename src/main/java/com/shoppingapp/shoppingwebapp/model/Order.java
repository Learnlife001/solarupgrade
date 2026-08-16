package com.shoppingapp.shoppingwebapp.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Table is named "orders" because ORDER is a reserved word in SQL.
 */
@Entity
@Table(name = "orders")
public class Order {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter SHORT_DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** Nullable: orders placed before payment selection existed have none. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    private PaymentMethod paymentMethod;

    /*
     * What the customer will actually be charged, snapshotted when the order
     * is placed.
     *
     * The total above is always naira, computed from the line snapshots. These
     * three record the conversion, if any, that the chosen payment method
     * required. They are stored rather than recalculated because the rate can
     * move between placing an order and paying for it, and the customer must
     * pay the figure they were quoted.
     */
    @Column(length = 3)
    private String paymentCurrency;

    @Column(precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    /** Naira per unit of paymentCurrency. Null when no conversion happened. */
    @Column(precision = 18, scale = 8)
    private BigDecimal exchangeRate;

    /**
     * When the "you have not finished paying" nudge went out. Recorded so the
     * reminder is sent once and only once -- an unpaid order that mails the
     * customer every hour is worse than one that never mails at all.
     */
    private Instant paymentReminderSentAt;

    @Column(nullable = false)
    private String shippingName;

    /*
     * The address is held as separate fields rather than one block of text.
     * A single textarea cannot be validated, cannot be handed to a courier's
     * API, and cannot be sorted or searched on afterwards.
     *
     * Line 2 and postcode are nullable because plenty of real addresses have
     * neither -- most of Nigeria has no postcode in daily use. City and state
     * are nullable only so that orders placed before this split still load;
     * the checkout form requires them.
     */
    @Column(nullable = false)
    private String shippingLine1;

    private String shippingLine2;

    private String shippingCity;

    private String shippingState;

    private String shippingPostcode;

    /** ISO 3166-1 alpha-2, so it is unambiguous to a courier or a tax rule. */
    @Column(length = 2)
    private String shippingCountry;

    @Column(nullable = false)
    private Instant placedAt = Instant.now();

    protected Order() {
        // required by JPA
    }

    public Order(User user, String shippingName, String shippingLine1) {
        this.user = user;
        this.shippingName = shippingName;
        this.shippingLine1 = shippingLine1;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Computed from the per-line price snapshots, so a later catalogue price
     * change never rewrites the value of an order already placed.
     */
    public BigDecimal getTotal() {
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getItemCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void recordCharge(String currency, BigDecimal amount, BigDecimal rate) {
        this.paymentCurrency = currency;
        this.paymentAmount = amount;
        this.exchangeRate = rate;
    }

    public String getPaymentCurrency() {
        return paymentCurrency;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    /** Naira, always: the shop's own books are kept in one currency. */
    public String getTotalDisplay() {
        return Money.base(getTotal());
    }

    /**
     * What the customer is charged, in the currency they are charged in.
     * Falls back to the naira total for orders placed before this was recorded.
     */
    public String getChargeDisplay() {
        return paymentAmount == null
                ? getTotalDisplay()
                : Money.format(paymentAmount, paymentCurrency);
    }

    /** True when the charge is in a different currency from the shop's base. */
    public boolean isConverted() {
        return exchangeRate != null;
    }

    /** The rate this order was actually converted at, not today's. */
    public String getExchangeRateDisplay() {
        return exchangeRate == null ? "" : Money.base(exchangeRate);
    }

    public Instant getPaymentReminderSentAt() {
        return paymentReminderSentAt;
    }

    public void markPaymentReminderSent() {
        this.paymentReminderSentAt = Instant.now();
    }

    public String getShippingName() {
        return shippingName;
    }

    public String getShippingLine1() {
        return shippingLine1;
    }

    public void setShippingLine2(String shippingLine2) {
        this.shippingLine2 = shippingLine2;
    }

    public String getShippingLine2() {
        return shippingLine2;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingState(String shippingState) {
        this.shippingState = shippingState;
    }

    public String getShippingState() {
        return shippingState;
    }

    public void setShippingPostcode(String shippingPostcode) {
        this.shippingPostcode = shippingPostcode;
    }

    public String getShippingPostcode() {
        return shippingPostcode;
    }

    public void setShippingCountry(String shippingCountry) {
        this.shippingCountry = shippingCountry;
    }

    public String getShippingCountry() {
        return shippingCountry;
    }

    /**
     * The address as a courier would write it: one entry per line, blanks
     * dropped. Views render this as separate lines rather than a text blob.
     */
    public List<String> getShippingLines() {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, shippingLine1);
        addIfPresent(lines, shippingLine2);
        // City and postcode share a line, the way an address label reads.
        String cityLine = Stream.of(shippingCity, shippingPostcode)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        addIfPresent(lines, cityLine);
        addIfPresent(lines, shippingState);
        addIfPresent(lines, countryName());
        return lines;
    }

    /** Full country name from the stored code, for display. */
    public String countryName() {
        if (shippingCountry == null || shippingCountry.isBlank()) {
            return null;
        }
        String name = Locale.of("", shippingCountry).getDisplayCountry(Locale.ENGLISH);
        return name.isBlank() ? shippingCountry : name;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value.trim());
        }
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    /**
     * Pre-formatted for the views, so the templates do not need a date dialect.
     */
    public String getPlacedAtDisplay() {
        return DISPLAY_FORMAT.format(placedAt);
    }

    public String getPlacedAtShortDisplay() {
        return SHORT_DISPLAY_FORMAT.format(placedAt);
    }
}
