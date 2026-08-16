-- Verify with a typed code rather than a clicked link.
--
-- A link is convenient but travels badly: it wraps in plain-text mail, breaks
-- when a client rewrites URLs, and cannot be carried from a phone to a laptop.
-- A six-digit code can simply be read and typed.
--
-- The trade-off is that a code is guessable where a 32-byte token is not, so
-- verification_attempts caps wrong guesses and the code lives minutes rather
-- than a day. Both are enforced in the entity.

ALTER TABLE users DROP COLUMN verification_token;
ALTER TABLE users DROP COLUMN verification_token_expires_at;

ALTER TABLE users ADD COLUMN verification_code VARCHAR(6);
ALTER TABLE users ADD COLUMN verification_code_expires_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN verification_attempts INTEGER NOT NULL DEFAULT 0;
