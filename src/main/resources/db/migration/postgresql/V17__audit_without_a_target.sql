-- Not every recorded action is about one row; see the h2 copy.
ALTER TABLE admin_actions ALTER COLUMN target_id DROP NOT NULL;
