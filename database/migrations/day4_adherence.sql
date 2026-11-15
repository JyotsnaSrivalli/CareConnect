-- Day 4 migration. Run ONCE in MySQL Workbench. Additive only: nothing is dropped, no rows are changed.
USE careconnect;

-- 1) Schedules are now soft-deleted. Existing schedules stay active.
ALTER TABLE medication_schedules
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER dose_time;

-- 2) Each log remembers exactly how much stock it deducted, so undo restores that exact amount
--    even if the medicine's dosage is edited afterwards.
ALTER TABLE medication_logs
    ADD COLUMN quantity_deducted DECIMAL(8,2) NOT NULL DEFAULT 0 AFTER status;
