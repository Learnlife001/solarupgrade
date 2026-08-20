package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.Order;
import com.shoppingapp.shoppingwebapp.model.OrderStatus;
import com.shoppingapp.shoppingwebapp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    /*
     * Admin views. Every order, not one customer's -- the only place in the
     * application that reads across users, which is why the paths that use
     * these are the only ones behind ROLE_ADMIN.
     *
     * The user is fetched alongside the items because the admin list shows who
     * ordered, and open-in-view is off: a lazy read of order.user would fail
     * once the template started rendering.
     */
    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<Order> findAllByOrderByPlacedAtDesc();

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<Order> findByStatusOrderByPlacedAtDesc(OrderStatus status);

    /*
     * Paging, in two steps.
     *
     * A Pageable on a query that fetches a collection does not page in SQL.
     * Hibernate fetches every matching row, joins the items, and applies the
     * offset and limit in memory -- warning HHH90003004 as it goes -- which is
     * the opposite of what paging is for: the page looks fixed while the
     * database work grows with every order ever placed.
     *
     * So the ids are paged on their own, where there is no collection to join,
     * and then that page of ids is fetched with the graph the templates need.
     * Two queries, both bounded.
     */
    @Query("select o.id from Order o")
    Page<Long> pageOrderIds(Pageable pageable);

    @Query("select o.id from Order o where o.status = :status")
    Page<Long> pageOrderIdsByStatus(@Param("status") OrderStatus status, Pageable pageable);

    /**
     * The admin search: order number, the customer's address, or the name on
     * the parcel. Those are the three things somebody has in front of them
     * when they go looking -- an email asking where an order is, a note with a
     * number on it, or a name.
     *
     * <p>The term is an empty string when unused, never null. A null inside
     * {@code lower()} has no type for PostgreSQL to infer and the statement
     * fails with "function lower(bytea) does not exist" -- which H2 accepts,
     * so it passed every test and broke only in production the last time.
     *
     * <p>The count query is spelled out rather than derived, because a derived
     * one guesses at the shape of the select and the guess is worth not
     * relying on for the number that decides how many pages there are.
     */
    @Query(value = """
            select o.id from Order o
            where (:status is null or o.status = :status)
              and (:term = ''
                   or lower(o.user.email) like lower(concat('%', :term, '%'))
                   or lower(o.shippingName) like lower(concat('%', :term, '%'))
                   or str(o.id) = :term)
            """,
            countQuery = """
            select count(o.id) from Order o
            where (:status is null or o.status = :status)
              and (:term = ''
                   or lower(o.user.email) like lower(concat('%', :term, '%'))
                   or lower(o.shippingName) like lower(concat('%', :term, '%'))
                   or str(o.id) = :term)
            """)
    Page<Long> searchOrderIds(@Param("status") OrderStatus status,
                              @Param("term") String term,
                              Pageable pageable);

    @Query("select o.id from Order o where o.user = :user")
    Page<Long> pageOrderIdsByUser(@Param("user") User user, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<Order> findByIdInOrderByPlacedAtDesc(Collection<Long> ids);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findWithItemsById(Long id);

    long countByStatus(OrderStatus status);
}
