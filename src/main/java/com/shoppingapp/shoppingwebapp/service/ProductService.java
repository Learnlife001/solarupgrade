package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import com.shoppingapp.shoppingwebapp.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findByCategory(Category category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        return productRepository.findByNameContainingIgnoreCase(query.trim());
    }

    /**
     * The detail page's product, with its specification rows already loaded.
     */
    public Product getWithSpecs(Long id) {
        return productRepository.findWithSpecsById(id)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));
    }

    /**
     * Products from the categories this one is usually bought alongside.
     *
     * <p>Only what is in stock, never the product being viewed, and capped so
     * the strip stays a suggestion rather than a second catalogue.
     */
    public List<Product> pairsWith(Product product, int limit) {
        return productRepository
                .findByCategoryInAndStockGreaterThanAndIdNot(
                        product.getCategory().pairsWith(), 0, product.getId())
                .stream()
                .limit(limit)
                .toList();
    }

    /** Admin stock list: whatever is running out is at the top, where it belongs. */
    public List<Product> byStockAscending() {
        return productRepository.findAllByOrderByStockAscNameAsc();
    }

    public long countLowStock(int threshold) {
        return productRepository.countByStockLessThan(threshold);
    }

    /**
     * Sets stock to an absolute figure, taken from a count of the shelf.
     *
     * <p>Absolute rather than a delta because the person typing it is looking
     * at the actual pile. A locked row and a re-read first, since orders are
     * decrementing the same number: without them, a stock take started before
     * a sale and saved after it would silently undo that sale.
     */
    @Transactional
    public Product setStock(Long id, int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        product.setStock(stock);
        return productRepository.save(product);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));
    }
}
