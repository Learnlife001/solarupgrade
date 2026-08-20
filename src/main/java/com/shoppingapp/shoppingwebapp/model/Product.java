package com.shoppingapp.shoppingwebapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /**
     * Money is stored as DECIMAL rather than a floating point type so that
     * order totals stay exact.
     */
    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Stored as VARCHAR rather than a native DB enum type, so adding a category
     * does not require a schema migration.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private Category category;

    @PositiveOrZero
    @Column(nullable = false)
    private int stock;

    private String imageUrl;

    /**
     * Retired from the shop, but not gone.
     *
     * <p>A product cannot simply be deleted: {@code order_items} point at it,
     * and an order from last month has to keep saying what it was for. So a
     * product that is no longer sold is archived -- hidden from the catalogue,
     * still listed in the admin area, still attached to every order that bought
     * it, and restorable if it comes back.
     */
    @Column(nullable = false)
    private boolean archived = false;

    /**
     * Ordered by sort_order so the table reads the way it was written rather
     * than in whatever order the rows come back.
     */
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ProductSpec> specs = new ArrayList<>();

    protected Product() {
        // required by JPA
    }

    public Product(String name, String description, BigDecimal price, Category category, int stock, String imageUrl) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stock = stock;
        this.imageUrl = imageUrl;
    }

    public boolean isInStock() {
        return stock > 0;
    }

    /**
     * Artwork for the views. Falls back to the category illustration when a
     * product has no image of its own, so the catalogue never renders a gap.
     */
    public String getImage() {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }
        return "/images/" + category.name().toLowerCase(Locale.ROOT) + ".svg";
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    /** Pre-formatted for the views, so no template repeats a format call. */
    public String getPriceDisplay() {
        return Money.base(price);
    }

    public List<ProductSpec> getSpecs() {
        return specs;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isArchived() {
        return archived;
    }

    public void archive() {
        this.archived = true;
    }

    public void restore() {
        this.archived = false;
    }
}
