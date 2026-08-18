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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One thing an administrator did, kept for good.
 *
 * <p>These actions were only ever written to the application log. Render's logs
 * expire, so "who cancelled this order, and when?" became unanswerable within
 * days -- exactly the question that gets asked when a customer disputes
 * something months later.
 *
 * <p>Deliberately append-only. There is no setter, no update path and no delete
 * anywhere in the application: a record that can be edited by the person it
 * describes is not a record. Rows keep their own copy of who acted and what was
 * affected rather than pointing at the user and order rows, so deleting either
 * cannot quietly empty the history.
 */
@Entity
@Table(name = "admin_actions")
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The administrator's email, copied at the time. */
    @Column(nullable = false, length = 255)
    private String actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private AdminActionType action;

    /** "order" or "product" -- what kind of thing was acted on. */
    @Column(nullable = false, length = 32)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    /** What changed, in words, for a human reading the history later. */
    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant happenedAt = Instant.now();

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK).withZone(ZoneId.of("Africa/Lagos"));

    protected AdminAction() {
        // required by JPA
    }

    public AdminAction(String actor, AdminActionType action, String targetType, Long targetId, String detail) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public AdminActionType getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getHappenedAt() {
        return happenedAt;
    }

    /** Lagos time, because that is where whoever reads this history works. */
    public String getHappenedAtDisplay() {
        return DISPLAY_FORMAT.format(happenedAt);
    }
}
