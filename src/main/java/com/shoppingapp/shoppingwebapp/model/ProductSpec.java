package com.shoppingapp.shoppingwebapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line of a product's specification table.
 *
 * <p>Key and value rather than columns on Product, because the attributes
 * genuinely differ by category: a panel has a cell count, a battery has a
 * chemistry, a mounting kit has a wind load. Columns would be a wide table
 * that is mostly null.
 */
@Entity
@Table(name = "product_specs")
public class ProductSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 80)
    private String label;

    /** Column is spec_value: VALUE is a reserved word in H2. */
    @Column(name = "spec_value", nullable = false, length = 160)
    private String value;

    /** Reading order, so the figure that matters most is not buried. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductSpec() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
