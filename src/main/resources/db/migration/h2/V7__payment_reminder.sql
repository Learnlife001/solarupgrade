-- Tracks the single "you have not finished paying" nudge, so the reminder job
-- can tell an order it has already chased from one it has not. Without this it
-- would mail the same customer on every run.

ALTER TABLE orders ADD COLUMN payment_reminder_sent_at TIMESTAMP(6) WITH TIME ZONE;
