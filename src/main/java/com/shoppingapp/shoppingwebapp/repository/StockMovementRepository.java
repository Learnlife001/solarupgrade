package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.StockMovement;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** One product's history, newest first, capped for the page that shows it. */
    List<StockMovement> findByProductIdOrderByHappenedAtDescIdDesc(Long productId, Limit limit);
}
