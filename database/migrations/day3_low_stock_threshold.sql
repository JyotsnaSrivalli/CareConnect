-- Day 3: run ONCE in MySQL Workbench against the existing careconnect database.
USE careconnect;
ALTER TABLE medication_inventory
    ADD COLUMN low_stock_threshold DECIMAL(8,2) NOT NULL DEFAULT 0 AFTER remaining_quantity;
