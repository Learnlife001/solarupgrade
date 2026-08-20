package com.shoppingapp.shoppingwebapp.service;

import com.shoppingapp.shoppingwebapp.model.AdminAction;
import com.shoppingapp.shoppingwebapp.model.AdminActionType;
import com.shoppingapp.shoppingwebapp.repository.AdminActionRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes and reads the record of what administrators did.
 *
 * <p>Recording happens after the action succeeded, never before: an audit row
 * for something that then failed is worse than no row, because it is a
 * confident statement of something untrue.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    public static final String ORDER = "order";
    public static final String PRODUCT = "product";
    public static final String SUPPLIER = "supplier";

    private final AdminActionRepository actions;

    public AuditService(AdminActionRepository actions) {
        this.actions = actions;
    }

    @Transactional
    public void record(String actor, AdminActionType action, String targetType, Long targetId, String detail) {
        actions.save(new AdminAction(actor, action, targetType, targetId, detail));
    }

    /**
     * An action with no single row behind it, such as exporting every order.
     * Its own method so a caller never has to pass a null and wonder whether
     * that was allowed.
     */
    public void record(String actor, AdminActionType action, String targetType, String detail) {
        record(actor, action, targetType, null, detail);
    }

    /** Newest first, for the dashboard. */
    public List<AdminAction> recent(int limit) {
        return actions.findByOrderByHappenedAtDesc(Limit.of(limit));
    }

    public List<AdminAction> forOrder(Long orderId) {
        return actions.findByTargetTypeAndTargetIdOrderByHappenedAtDesc(ORDER, orderId);
    }
}
