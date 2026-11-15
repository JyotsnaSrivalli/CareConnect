-- =====================================================================
-- CareConnect  Day 5  -  Daily well-being check-in  (ADDITIVE, SAFE)
-- =====================================================================
-- Run ONCE, on a database that already has the Day 1-4 tables.
-- (A brand-new database created from database/schema.sql already contains
--  these changes - do NOT run this file on it, MySQL would complain about
--  duplicate columns.)
--
-- What it does to the existing `daily_checkins` table:
--   * keeps every existing row and every existing column
--   * feeling        : adds two NEW allowed values at the END of the list
--                      ('OKAY', 'NOT_GOOD').  Old values stay valid.
--   * took_medicines : changed from NOT NULL to NULL-allowed, because the
--                      Day 5 form does not ask this question any more.
--                      Existing values are untouched.
--   * energy_level   : NEW nullable column (HIGH / MODERATE / LOW)
--   * symptoms       : NEW nullable column
--   * updated_at     : NEW nullable column
--   * `comments`     : reused as the "notes" field (no change)
--
-- It never drops a table, drops a column, or deletes / updates a row.
-- =====================================================================

USE careconnect;

-- STEP 0 (look before you change): how many rows exist right now?
-- Write the number down. It must be the same after the migration.
SELECT COUNT(*) AS rows_before FROM daily_checkins;

-- STEP 1: the migration itself (one statement, so it succeeds or fails as a whole)
ALTER TABLE daily_checkins
    MODIFY COLUMN feeling
        ENUM('VERY_GOOD','GOOD','NORMAL','FEELING_LOW','FEELING_UNWELL','OKAY','NOT_GOOD') NOT NULL,
    MODIFY COLUMN took_medicines
        ENUM('YES','NO','MISSED_SOME') NULL,
    ADD COLUMN energy_level ENUM('HIGH','MODERATE','LOW') NULL AFTER feeling,
    ADD COLUMN symptoms     VARCHAR(500) NULL AFTER took_medicines,
    ADD COLUMN updated_at   TIMESTAMP NULL AFTER created_at;

-- STEP 2 (verify): the row count must equal rows_before, and the new columns must exist
SELECT COUNT(*) AS rows_after FROM daily_checkins;
DESCRIBE daily_checkins;
