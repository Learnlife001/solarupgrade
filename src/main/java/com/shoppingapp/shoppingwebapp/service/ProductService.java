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
    private final StockService stockService;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductService(ProductRepository productRepository, StockService stockService) {
        this.productRepository = productRepository;
        this.stockService = stockService;
    }

    /** What the shop sells today. Archived products are not it. */
    public List<Product> findAll() {
        return productRepository.findByArchivedFalse();
    }

    public List<Product> findByCategory(Category category) {
        return productRepository.findByCategoryAndArchivedFalse(category);
    }

    public List<Product> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        return productRepository.findByNameContainingIgnoreCaseAndArchivedFalse(query.trim());
    }

    /**
     * The detail page's product, with its specification rows already loaded.
     *
     * <p>An archived product is treated as missing here. Its page would
     * otherwise stay reachable by an old link or a search engine, with an
     * "add to basket" button on something we have stopped selling.
     */
    public Product getWithSpecs(Long id) {
        return productRepository.findWithSpecsById(id)
                .filter(product -> !product.isArchived())
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
                .findByCategoryInAndStockGreaterThanAndIdNotAndArchivedFalse(
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
    public Product setStock(Long id, int stock, String actor) {
        if (stock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        // Through StockService, which records the difference the count made.
        // A stock take that leaves no trace is why the figure could not be
        // explained in the first place.
        return stockService.countedAt(product, stock, actor);
    }

    /**
     * Any product, archived or not. For the admin area, which has to be able to
     * see and restore what the shop is hiding.
     */
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));
    }

    /**
     * A product a customer is allowed to act on.
     *
     * <p>The basket goes through this rather than {@link #getById}: hiding a
     * product from the catalogue while still accepting "add to basket" for its
     * id would be a hole rather than a retirement, and the id is in the page
     * source of every order that ever contained it.
     */
    public Product getSellable(Long id) {
        Product product = getById(id);
        if (product.isArchived()) {
            throw new NoSuchElementException("Product " + id + " is no longer sold");
        }
        return product;
    }

    /** Everything, newest last. The admin list, which shows archived rows too. */
    public List<Product> all() {
        return productRepository.findAll();
    }

    @Transactional
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public Product archive(Long id) {
        Product product = getById(id);
        product.archive();
        return productRepository.save(product);
    }

    @Transactional
    public Product restore(Long id) {
        Product product = getById(id);
        product.restore();
        return productRepository.save(product);
    }
}
