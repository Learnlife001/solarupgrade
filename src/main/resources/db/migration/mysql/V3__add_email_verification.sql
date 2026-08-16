-- Email verification. See the H2/PostgreSQL copies for the rationale; MySQL
-- differs only in the column types Hibernate expects (BIT, DATETIME).

ALTER TABLE users ADD COLUMN email_verified BIT NOT NULL DEFAULT b'0';
ALTER TABLE users ADD COLUMN verification_token VARCHAR(64);
ALTER TABLE users ADD COLUMN verification_token_expires_at DATETIME(6);

UPDATE users SET email_verified = b'1';
