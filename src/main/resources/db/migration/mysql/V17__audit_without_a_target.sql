-- Not every recorded action is about one row; see the h2 copy.
ALTER TABLE admin_actions MODIFY target_id BIGINT NULL;
