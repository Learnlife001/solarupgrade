-- Verify with a typed code rather than a clicked link. See the H2/PostgreSQL
-- copies for the rationale; MySQL differs only in the timestamp type.

ALTER TABLE users DROP COLUMN verification_token;
ALTER TABLE users DROP COLUMN verification_token_expires_at;

ALTER TABLE users ADD COLUMN verification_code VARCHAR(6);
ALTER TABLE users ADD COLUMN verification_code_expires_at DATETIME(6);
ALTER TABLE users ADD COLUMN verification_attempts INTEGER NOT NULL DEFAULT 0;
