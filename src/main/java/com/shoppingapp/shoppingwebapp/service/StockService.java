package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.model.StockMovement;
import com.shoppingapp.shoppingwebapp.model.StockMovementReason;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import com.shoppingapp.shoppingwebapp.repository.StockMovementRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only way stock moves.
 *
 * <p>It was moved from four places -- checkout, cancellation, refund and the
 * back office -- each doing {@code setStock} and saving. Three of those left no
 * trace, so the stock figure could only be explained by whoever last typed one,
 * and "I counted ten onto the shelf and it says three" had no answer at all.
 *
 * <p>Now every change goes through {@link #move}, which writes the movement in
 * the same transaction as the change. Recording is not a step a caller can
 * forget, because there is no other way to alter the number.
 *
 * <p>This does no locking of its own. Callers that race -- checkout and
 * cancellation both do -- take the row lock first, as they already did; this
 * runs inside that.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final ProductRepository products;
    private final StockMovementRepository movements;

    public StockService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    /**
     * Changes a product's stock by a signed amount and records why.
     *
     * @param change negative for a sale, positive for stock coming back
     * @param orderId the order behind it, or null for a stock take
     * @param actor the person, or null when the shop did it on its own
     * @return the product, saved
     */
    @Transactional
    public Product move(Product product, int change, StockMovementReason reason,
                        Long orderId, String actor) {
        int resulting = product.getStock() + change;
        if (resulting < 0) {
            // The callers all check first; this is the backstop that turns a
            // logic error into a refusal rather than a negative shelf.
            throw new IllegalStateException("Stock for " + product.getName()
                    + " cannot go below zero (" + product.getStock() + " with " + change + ")");
        }

        product.setStock(resulting);
        Product saved = products.save(product);
        movements.save(new StockMovement(saved, change, resulting, reason, orderId, actor));
        return saved;
    }

    /**
     * Sets stock to an absolute figure, as a stock take does, and records the
     * difference it made.
     */
    @Transactional
    public Product countedAt(Product product, int counted, String actor) {
        if (counted < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        return move(product, counted - product.getStock(), StockMovementReason.STOCK_TAKE, null, actor);
    }

    /** One product's recent history, newest first. */
    public List<StockMovement> historyFor(Long productId, int limit) {
        return movements.findByProductIdOrderByHappenedAtDescIdDesc(productId, Limit.of(limit));
    }
}
