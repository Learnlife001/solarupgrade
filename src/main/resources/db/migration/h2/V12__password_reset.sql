-- Password reset.
--
-- The column holds a SHA-256 of the token, never the token itself. Whoever
-- holds a live reset token can take the account, so a database dump must not
-- contain any usable ones -- the same reasoning that keeps passwords out of
-- this table in the clear.
--
-- 64 characters because SHA-256 in lower-case hex is exactly that long.

ALTER TABLE users ADD COLUMN reset_token_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN reset_token_expires_at TIMESTAMP(6) WITH TIME ZONE;
