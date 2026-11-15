# Database Design

MySQL 8. Source of truth: `database/schema.sql`.

## ER Diagram

```mermaid
erDiagram
    USERS ||--o{ FAMILY_CONNECTIONS : "parent"
    USERS ||--o{ FAMILY_CONNECTIONS : "child"
    USERS ||--o{ INVITATION_TOKENS : generates
    USERS ||--o{ MEDICATIONS : owns
    USERS ||--o{ DAILY_CHECKINS : submits
    MEDICATIONS ||--o{ MEDICATION_SCHEDULES : "has dose times"
    MEDICATIONS ||--|| MEDICATION_INVENTORY : "has stock"
    MEDICATIONS ||--o{ MEDICATION_LOGS : "dose history"
    MEDICATION_SCHEDULES ||--o{ MEDICATION_LOGS : "confirmed per day"

    USERS { bigint id PK; string email UK; string role }
    FAMILY_CONNECTIONS { bigint parent_id FK; bigint child_id FK; string status; boolean share_checkins }
    MEDICATIONS { bigint id PK; bigint parent_id FK; string name; string type; boolean active }
    MEDICATION_SCHEDULES { bigint medication_id FK; time dose_time }
    MEDICATION_INVENTORY { bigint medication_id FK_UK; decimal total_quantity; decimal remaining_quantity }
    MEDICATION_LOGS { bigint schedule_id FK; date scheduled_date; string status }
```

## Key design decisions

- **Stock vs adherence are separate tables.** `medication_inventory` = physical quantity. `medication_logs` = confirmed doses. Taking a dose writes to both in one transaction, but neither is computed from the other.
- **Pending, not missed.** A dose is "pending" when no `TAKEN` log exists for (schedule, date). The system never writes a "missed" status.
- **Duplicate prevention.** `UNIQUE(schedule_id, scheduled_date)` makes a second submit fail at DB level. Undo sets status `UNDONE` and restores stock; re-taking flips it back to `TAKEN`.
- **Authorization.** A child may read a parent's data only if `family_connections` has `status = 'APPROVED'` for that pair. Check-ins additionally require `share_checkins = true`.
- **Soft delete** for medications (`active = false`) so history survives.
- **One check-in per day (Day 5).** `daily_checkins` has `UNIQUE(parent_id, checkin_date)`; the service updates the existing row instead of inserting a second one. `feeling` is `GOOD`/`OKAY`/`NOT_GOOD` (the older enum values stay in the column only so old rows remain valid), `energy_level` is `HIGH`/`MODERATE`/`LOW`, `comments` holds the notes, `symptoms` and `updated_at` were added by `day5_checkins_migration.sql`. The old `took_medicines` column is nullable and no longer used: confirmed adherence comes from the tablet and dose records, not from a self-report.
- **Adherence is calculated, never stored (Day 5).** The summary reads `medication_tablets` (tablets/capsules), `medication_logs` (syrup) and the active schedules at the moment it is requested. Nothing is written. Because schedules are edited in place, expected doses are only calculated for today; past dates show confirmed counts only.
