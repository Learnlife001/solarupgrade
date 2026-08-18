package com.shoppingapp.shoppingwebapp.repository;

import com.shoppingapp.shoppingwebapp.model.AdminAction;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads only. Nothing in the application updates or deletes an audit row, and
 * there is deliberately no method here that would let it.
 */
public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findByOrderByHappenedAtDesc(Limit limit);

    /** The history of one thing, for the page that shows it. */
    List<AdminAction> findByTargetTypeAndTargetIdOrderByHappenedAtDesc(String targetType, Long targetId);
}
