package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // items.product is fetched too: open-in-view is off, so the order pages
    // would otherwise fail on the first lazy read of a product image.
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByUserOrderByPlacedAtDesc(User user);

    // items.product is fetched too: open-in-view is off, so the order pages
    // would otherwise fail on the first lazy read of a product image.
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findByIdAndUser(Long id, User user);

    /** Unpaid, not yet chased, and old enough to be worth chasing. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByStatusAndPaymentReminderSentAtIsNullAndPlacedAtBefore(
            OrderStatus status, Instant placedBefore);

    /** Unpaid and past the point where its stock should go back on the shelf. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByStatusAndPlacedAtBefore(OrderStatus status, Instant placedBefore);
}
