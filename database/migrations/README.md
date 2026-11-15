# Database migrations

Run the files in order, once each, in MySQL Workbench against the `careconnect` database.

| File | What it does | Safe? |
|---|---|---|
| day3_low_stock_threshold.sql | adds `medication_inventory.low_stock_threshold` | additive |
| day4_adherence.sql | adds `medication_schedules.active` (default TRUE) and `medication_logs.quantity_deducted` (default 0) | additive: nothing dropped, no rows changed, existing schedules stay active |

## Why Day 4 needs them
- **`medication_schedules.active`**: schedules were hard-deleted, and `fk_log_sched` is `ON DELETE CASCADE`, so deleting a schedule would have
  deleted its dose history. The app now never deletes schedules; it sets `active = false`.
- **`medication_logs.quantity_deducted`**: stores exactly what a dose took out of stock, so undo restores that exact amount even if the
  medicine's dosage is edited later.
- The existing `status ENUM('TAKEN','UNDONE')` is reused as the reversal mechanism; no enum change was needed.

## Not changed on purpose
The `ON DELETE CASCADE` foreign keys are untouched (no destructive change). The application never hard-deletes schedules, medications or
users, so the cascades cannot fire from normal use.

## Check after running
```sql
DESCRIBE medication_schedules;   -- shows `active`
DESCRIBE medication_logs;        -- shows `quantity_deducted`
```

| day4b_tablet_strips.sql | creates `medication_tablets` (one row per physical tablet) and back-fills compartments for existing tablet/capsule medicines | additive: no table dropped, no rows deleted. Back-filled taken tablets have an unknown time (`taken_at` NULL). Run its first SELECT: it must return 0 rows (a strip holds at most 200 tablets). |

## Why Day 4b needs it
`medication_logs` records one row per scheduled dose per day, so it cannot say *which tablet* was taken. A compartment needs its own
record, otherwise its state could not survive a browser refresh and "undo that exact tablet" would be impossible.
For tablets/capsules, stock is now the number of AVAILABLE rows in `medication_tablets`; `medication_inventory` keeps the same numbers
(current = available, total = compartments) so low-stock warnings and the stock panel keep working.

## Check after running
```sql
SELECT medication_id, COUNT(*) AS compartments, SUM(status='TAKEN') AS taken FROM medication_tablets GROUP BY medication_id;
-- compartments should equal medication_inventory.total_quantity, and (compartments - taken) = remaining_quantity
```

| day5_checkins_migration.sql | extends the existing `daily_checkins` table for the Day 5 well-being check-in | additive: no table dropped, no column dropped, no row deleted or changed. Run once, on an existing database only. |

## Why Day 5 needs it
`daily_checkins` was created on Day 1 but never used by any code. It had no energy level or symptoms column, and `took_medicines` was
required. The migration adds `energy_level`, `symptoms` and `updated_at`, adds the new `feeling` values `OKAY` and `NOT_GOOD` at the end of
the enum, and lets `took_medicines` be empty. The old enum values stay valid, so any rows that already exist keep working.
`took_medicines` is deliberately not used any more: confirmed adherence comes from the Day 4 tablet/dose records, not from a self-report.

## Check after running
```sql
SELECT COUNT(*) FROM daily_checkins;   -- must equal the number you saw BEFORE the migration
DESCRIBE daily_checkins;               -- shows energy_level, symptoms, updated_at; took_medicines allows NULL
```
