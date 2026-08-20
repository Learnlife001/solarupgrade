-- Not every recorded action is about one row.
--
-- Downloading every order is an admin action worth remembering -- it is a bulk
-- read of every customer's name and address -- but it has no single order to
-- point at. The column was NOT NULL, which left only two options: invent an id,
-- or not record the action. Both are worse than a nullable column.
ALTER TABLE admin_actions ALTER COLUMN target_id SET NULL;
