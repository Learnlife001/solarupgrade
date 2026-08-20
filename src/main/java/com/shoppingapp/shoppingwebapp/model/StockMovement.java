package com.shoppingapp.shoppingwebapp.model;

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
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One change to a product's stock, and why.
 *
 * <p>Without this, "I counted ten onto the shelf and the system says three" has
 * no answer: sales, cancellations and refunds all moved the figure silently,
 * and the only recorded stock changes were the ones an administrator typed. A
 * shop where the stock number cannot be explained is one where nobody trusts
 * the stock number, and the usual next step is a spreadsheet kept beside it.
 *
 * <p>Append-only, like the admin audit trail: rows are written and read, never
 * updated. The point is what happened, and a record that can be edited is a
 * record of what somebody last decided it should say.
 *
 * <p>{@link #resultingStock} is stored rather than derived. Replaying every
 * movement to find out what the figure was on Tuesday means trusting that no
 * row is missing; storing the result at each step means a gap shows up as an
 * inconsistency instead of quietly changing the answer.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)
                    .withZone(ZoneId.of("Africa/Lagos"));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Lazy: the movement list is read for one product at a time, which is
     * already in hand, and the admin history page would otherwise fetch the
     * same product once per row.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Signed: negative for a sale, positive for stock coming back.
     *
     * <p>The column is {@code quantity_change} rather than {@code change},
     * which is a reserved word in MySQL. Naming it around the problem is
     * cheaper than quoting it differently in three migrations.
     */
    @Column(name = "quantity_change", nullable = false)
    private int change;

    /** What the figure became, so the history reads without arithmetic. */
    @Column(name = "resulting_stock", nullable = false)
    private int resultingStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockMovementReason reason;

    /**
     * The order behind it, when there was one. Null for a stock take, which is
     * somebody counting a shelf rather than anything a customer did.
     */
    @Column(name = "order_id")
    private Long orderId;

    /** Who, when a person did it. Null when the shop did it on its own. */
    @Column(length = 255)
    private String actor;

    @Column(name = "happened_at", nullable = false)
    private Instant happenedAt = Instant.now();

    protected StockMovement() {
        // required by JPA
    }

    public StockMovement(Product product, int change, int resultingStock,
                         StockMovementReason reason, Long orderId, String actor) {
        this.product = product;
        this.change = change;
        this.resultingStock = resultingStock;
        this.reason = reason;
        this.orderId = orderId;
        this.actor = actor;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getChange() {
        return change;
    }

    /** "+3" or "-2", the way a ledger reads. */
    public String getChangeDisplay() {
        return (change > 0 ? "+" : "") + change;
    }

    public int getResultingStock() {
        return resultingStock;
    }

    public StockMovementReason getReason() {
        return reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getActor() {
        return actor;
    }

    public Instant getHappenedAt() {
        return happenedAt;
    }

    public String getHappenedAtDisplay() {
        return DISPLAY.format(happenedAt);
    }
}
