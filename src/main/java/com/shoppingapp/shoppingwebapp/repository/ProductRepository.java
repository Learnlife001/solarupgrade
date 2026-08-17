package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Category;
import com.shoppingapp.shoppingwebapp.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(Category category);

    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * The detail page renders the specification table, and open-in-view is off,
     * so the specs have to come back with the product or the first read of them
     * fails outside the transaction.
     */
    @EntityGraph(attributePaths = "specs")
    Optional<Product> findWithSpecsById(Long id);

    /** For the "pairs with" strip: in stock, and never the product being viewed. */
    List<Product> findByCategoryInAndStockGreaterThanAndIdNot(
            List<Category> categories, int minimumStock, Long excludedId);
}
