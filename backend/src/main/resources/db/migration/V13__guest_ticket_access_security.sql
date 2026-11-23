-- V13__guest_ticket_access_security.sql
-- 1. Guest Support Ticket Access Security:
--    Add column to store SHA-256 hash of the 256-bit guest access secret.
--    Old guest tickets without a hash fail closed.

ALTER TABLE support_tickets ADD COLUMN guest_access_hash VARCHAR(64) NULL;
