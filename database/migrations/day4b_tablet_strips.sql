-- Day 4b migration: individual tablet compartments. Run ONCE in MySQL Workbench.
-- Adds one new table. Nothing is dropped; medication_logs (syrup dose history) is not touched.
USE careconnect;

-- PRE-CHECK: a strip holds at most 200 tablets. This must return 0 rows before you continue.
SELECT i.medication_id, i.total_quantity
FROM medication_inventory i JOIN medications m ON m.id = i.medication_id
WHERE m.type IN ('TABLET','CAPSULE') AND i.total_quantity > 200;

-- One row per physical tablet/capsule. The row IS the compartment on the blister strip.
CREATE TABLE medication_tablets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    medication_id BIGINT NOT NULL,
    slot_number   INT NOT NULL,                                   -- 1, 2, 3 ... position on the strip
    status        ENUM('AVAILABLE','TAKEN') NOT NULL DEFAULT 'AVAILABLE',
    taken_at      TIMESTAMP NULL,                                 -- date + time of the confirmation
    CONSTRAINT uq_tablet_slot UNIQUE (medication_id, slot_number),
    CONSTRAINT fk_tablet_med FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE,
    INDEX idx_tablet_med_status (medication_id, status)
);

-- BACKFILL for medicines you already created: one compartment per whole tablet in total_quantity.
-- (total - remaining) of them are marked TAKEN with an unknown time (taken_at stays NULL), the rest AVAILABLE.
INSERT INTO medication_tablets (medication_id, slot_number, status, taken_at)
SELECT i.medication_id, s.n,
       IF(s.n <= FLOOR(i.total_quantity) - FLOOR(i.remaining_quantity), 'TAKEN', 'AVAILABLE'),
       NULL
FROM medication_inventory i
JOIN medications m ON m.id = i.medication_id AND m.type IN ('TABLET','CAPSULE')
JOIN (
    WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 200)
    SELECT n FROM seq
) s ON s.n <= FLOOR(i.total_quantity)
WHERE NOT EXISTS (SELECT 1 FROM medication_tablets t WHERE t.medication_id = i.medication_id);

-- Make stock whole numbers so stock == number of compartments from now on.
UPDATE medication_inventory i
JOIN medications m ON m.id = i.medication_id AND m.type IN ('TABLET','CAPSULE')
SET i.total_quantity = FLOOR(i.total_quantity), i.remaining_quantity = FLOOR(i.remaining_quantity);
