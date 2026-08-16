-- Email verification.
--
-- A new account is unverified and cannot sign in until the link sent to its
-- address is followed. Format validation alone proves nothing -- an address
-- can be well-formed and still belong to nobody.
--
-- Existing rows are backfilled as verified: they were created before this rule
-- existed, and silently locking them out would be a regression. New rows get
-- false from the entity, not from this default.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN verification_token VARCHAR(64);
ALTER TABLE users ADD COLUMN verification_token_expires_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE users SET email_verified = TRUE;
