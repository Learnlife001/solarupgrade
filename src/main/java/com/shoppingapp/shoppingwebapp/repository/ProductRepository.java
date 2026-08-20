package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /*
     * The shop only ever sees products that are not archived. Every one of
     * these carries AndArchivedFalse rather than the callers remembering to
     * filter: a forgotten filter puts a retired product back on sale, and the
     * first anyone hears of it is an order for something no longer stocked.
     */
    List<Product> findByArchivedFalse();

    List<Product> findByCategoryAndArchivedFalse(Category category);

    List<Product> findByNameContainingIgnoreCaseAndArchivedFalse(String name);

    /**
     * The detail page renders the specification table, and open-in-view is off,
     * so the specs have to come back with the product or the first read of them
     * fails outside the transaction.
     */
    @EntityGraph(attributePaths = "specs")
    Optional<Product> findWithSpecsById(Long id);

    /** Admin stock list: whatever is running out floats to the top. */
    List<Product> findAllByOrderByStockAscNameAsc();

    long countByStockLessThan(int threshold);

    /** For the "pairs with" strip: in stock, and never the product being viewed. */
    List<Product> findByCategoryInAndStockGreaterThanAndIdNotAndArchivedFalse(
            List<Category> categories, int minimumStock, Long excludedId);
}
