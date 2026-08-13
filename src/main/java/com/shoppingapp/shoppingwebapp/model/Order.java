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

    @Column(nullable = false)
    private String shippingName;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private String shippingPostcode;

    @Column(nullable = false)
    private Instant placedAt = Instant.now();

    protected Order() {
        // required by JPA
    }

    public Order(User user, String shippingName, String shippingAddress, String shippingPostcode) {
        this.user = user;
        this.shippingName = shippingName;
        this.shippingAddress = shippingAddress;
        this.shippingPostcode = shippingPostcode;
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

    public String getShippingName() {
        return shippingName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public String getShippingPostcode() {
        return shippingPostcode;
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
