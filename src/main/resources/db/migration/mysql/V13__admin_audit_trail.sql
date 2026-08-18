-- Audit trail for administrator actions.
--
-- These were only ever written to the application log, and Render's logs
-- expire, so "who cancelled this order, and when?" stopped being answerable
-- within days -- which is exactly the question asked when a customer disputes
-- something months later.
--
-- Append-only by intent: nothing in the application updates or deletes a row
-- here. The actor and target are copied rather than referenced, so deleting a
-- user or a product cannot quietly empty the history.

CREATE TABLE admin_actions (
    id          BIGINT       AUTO_INCREMENT,
    actor       VARCHAR(255) NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    target_type VARCHAR(32)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    detail      VARCHAR(500),
    happened_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

-- The two ways this table is read: the dashboard's recent activity, and one
-- order's own history.
CREATE INDEX idx_admin_actions_happened_at ON admin_actions (happened_at);
CREATE INDEX idx_admin_actions_target ON admin_actions (target_type, target_id);
