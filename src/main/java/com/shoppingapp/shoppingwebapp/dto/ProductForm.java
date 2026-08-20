package com.shoppingapp.shoppingwebapp.dto;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The admin form for a catalogue entry.
 *
 * <p>The price is a {@link BigDecimal} in naira with two decimal places, and it
 * is validated as such rather than parsed leniently. A price is the one field
 * where "near enough" is a refund conversation: a stray keystroke turning
 * 380,000 into 38,000 is a real loss on every sale until somebody notices.
 */
public class ProductForm {

    private Long id;

    @NotBlank(message = "A name is required")
    @Size(max = 160, message = "That name is too long for a product card")
    private String name = "";

    @NotBlank(message = "A description is required — it is what the shop page shows")
    @Size(max = 2000)
    private String description = "";

    /**
     * Naira. Above zero, because a free product in a shop that takes payment is
     * a mistake rather than a price, and capped at two decimals so nothing is
     * silently rounded on the way into the database.
     */
    @NotNull(message = "A price is required")
    @DecimalMin(value = "0.01", message = "A price must be more than zero")
    @Digits(integer = 10, fraction = 2, message = "A price takes at most two decimal places")
    private BigDecimal price;

    @NotNull(message = "Choose a category")
    private Category category;

    @Min(value = 0, message = "Stock cannot be negative")
    private int stock;

    /**
     * Chosen from the artwork that ships with the application; see
     * {@code ProductImages}. Blank is allowed — a product with no picture
     * renders a placeholder, which is better than blocking a listing on
     * artwork nobody has drawn yet.
     */
    private String imageUrl = "";

    public static ProductForm of(Product product) {
        ProductForm form = new ProductForm();
        form.id = product.getId();
        form.name = product.getName();
        form.description = product.getDescription();
        form.price = product.getPrice();
        form.category = product.getCategory();
        form.stock = product.getStock();
        form.imageUrl = product.getImageUrl() == null ? "" : product.getImageUrl();
        return form;
    }

    /**
     * Writes the form onto an existing product, <b>except its stock</b>.
     *
     * <p>Stock is deliberately left alone here. Setting it directly is how a
     * change escapes the ledger: the edit form did exactly that, so saving a
     * product moved its stock with no movement recorded, while the stock-take
     * control beside it recorded properly. A figure that is sometimes explained
     * is worse than one that never is, because it is trusted. The controller
     * routes a changed figure through {@code StockService} instead.
     */
    public void applyTo(Product product) {
        product.setName(name.trim());
        product.setDescription(description.trim());
        product.setPrice(price);
        product.setCategory(category);
        product.setImageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim());
    }

    /**
     * A new product with <b>no stock</b>, whatever the form said.
     *
     * <p>The opening figure is applied afterwards as a counted stock take, so
     * the ledger starts with the movement that put the units there rather than
     * with a quantity that appeared from nowhere.
     */
    public Product toNewProduct() {
        return new Product(name.trim(), description.trim(), price, category, 0,
                imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
