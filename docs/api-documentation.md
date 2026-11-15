# API Documentation (Day 2)

Base URL: `http://localhost:8080/api`. Protected endpoints need `Authorization: Bearer <token>`.
Errors: `{ "timestamp", "status", "message", "fieldErrors"? }`.

| Method | Path | Who | Success | Notes |
|---|---|---|---|---|
| GET | /health | public | 200 | `{app, database}` |
| POST | /auth/register | public | 201 | body `{name,email,password(8-72),role: PARENT\|CHILD}` -> `{token,user}`; 409 duplicate email; 400 validation |
| POST | /auth/login | public | 200 | body `{email,password}` -> `{token,user}`; 401 on bad credentials |
| GET | /auth/me | logged in | 200 | current user |
| POST | /family/invite | PARENT | 201 | `{code, expiresAt}`; code is 8 chars, single use, 24 h |
| POST | /family/connect | CHILD | 201 | body `{code}` -> connection with status PENDING; 404 unknown code; 400 used/expired; 409 already linked |
| POST | /family/approve | PARENT | 200 | body `{connectionId, approve, shareCheckins?}`; approve=false rejects or removes access; 404 if not your connection; 409 invalid transition |
| GET | /family/connections | logged in | 200 | parent: requests from children; child: own links to parents |

## Authorization rules
- 401: missing/invalid/expired token. 403: wrong role. 
- A parent can only load connections where `parent_id` is their own id (other parents get 404).
- `FamilyService.assertChildCanAccessParent(childId, parentId)` is the single guard every future "child reads parent data" endpoint must call; it requires status APPROVED.

---
# Day 3 – Medications, schedules, inventory

Writes (POST/PUT/DELETE) are PARENT only (403 for a child). Reads work for a parent (own data) and a child with an
APPROVED connection (otherwise 403). The owner is always taken from the JWT, never from the request.
A medication belonging to another parent is reported as 404.

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | /medications | 201 | 400 validation / dates, 403 child |
| GET | /medications | 200 | child must send `?parentId=` (400 if missing, 403 if not approved) |
| GET | /medications/{id} | 200 | 404, 403 |
| PUT | /medications/{id} | 200 | 400, 404 |
| DELETE | /medications/{id} | 204 (soft delete) | 404 |
| GET | /medications/{id}/schedules | 200 | 403, 404 |
| POST | /medications/{id}/schedules | 201 | 409 same time twice |
| PUT | /medications/{id}/schedules/{scheduleId} | 200 | 404, 409 |
| DELETE | /medications/{id}/schedules/{scheduleId} | 204 | 404 |
| GET | /medications/{id}/inventory | 200 | 403, 404 |
| PUT | /medications/{id}/inventory | 200 | 400 |

## Create medication
`POST /api/medications`
```json
{
  "name": "Paracetamol", "type": "TABLET", "strength": "500 mg", "dosagePerDose": 1,
  "frequencyLabel": "Twice daily", "foodInstruction": "AFTER_FOOD", "notes": "Take with water",
  "startDate": "2026-11-01", "endDate": null,
  "initialQuantity": 20, "lowStockThreshold": 5, "doseTimes": ["08:00", "20:00"]
}
```
`initialQuantity`, `lowStockThreshold` and `doseTimes` are only used on create (later changes go through the
inventory and schedule endpoints). `type` is TABLET | CAPSULE | SYRUP; the stock unit is derived from it
(tablets, capsules, ml). `foodInstruction` is BEFORE_FOOD | AFTER_FOOD | WITH_FOOD | ANY_TIME (default ANY_TIME).

Response `201`:
```json
{
  "id": 1, "parentId": 3, "name": "Paracetamol", "type": "TABLET", "strength": "500 mg",
  "dosagePerDose": 1, "quantityUnit": "TABLET", "frequencyLabel": "Twice daily",
  "foodInstruction": "AFTER_FOOD", "notes": "Take with water", "startDate": "2026-11-01", "endDate": null,
  "active": true, "createdAt": "2026-11-02T10:15:00",
  "schedules": [ { "id": 1, "doseTime": "08:00:00" }, { "id": 2, "doseTime": "20:00:00" } ],
  "inventory": { "totalQuantity": 20, "currentQuantity": 20, "lowStockThreshold": 5, "unit": "TABLET",
                 "lowStock": false, "updatedAt": "2026-11-02T10:15:00" }
}
```

## Add schedule
`POST /api/medications/1/schedules` body `{ "doseTime": "13:00" }` -> `201 { "id": 3, "doseTime": "13:00:00" }`

## Update stock
`PUT /api/medications/1/inventory` body `{ "currentQuantity": 5, "lowStockThreshold": 5 }`
```json
{ "totalQuantity": 20, "currentQuantity": 5, "lowStockThreshold": 5, "unit": "TABLET", "lowStock": true,
  "updatedAt": "2026-11-02T10:20:00" }
```
`lowStock` is true when `currentQuantity <= lowStockThreshold`. If `currentQuantity` is higher than the old
total (a refill) the total is raised to match.

## Child viewing a parent's medicines
`GET /api/medications?parentId=3` with the child's token -> same list shape as above, or 403 without an approved link.

---
# Day 4 – Dose tracking (adherence)

Rules: a dose is **PENDING** until the parent confirms it. Time passing never marks it taken or missed.
Confirm/undo are PARENT only (403 for a child). Reads work for a parent (own data; any `parentId` is ignored) and for a child
with an APPROVED connection (`?parentId=` required, else 400/403).

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /medication-logs/today | 200 list of doses | 400 child without parentId, 403 child not approved |
| GET | /medication-logs?date=YYYY-MM-DD | 200 | 400 bad/future date |
| GET | /medication-logs/summary?date= | 200 | same |
| POST | /medication-logs/{scheduleId}/taken?date= | 201 dose (TAKEN) | 400 future date / outside course, 404 not yours or inactive, 409 already taken or not enough stock |
| DELETE | /medication-logs/{logId} | 200 dose (PENDING again) | 404 not yours, 409 already undone |

`date` defaults to today (server local date). Only active medications and active schedules produce doses.
Confirmed doses of schedules/medications removed later stay visible for their day.

## Dose
```json
{ "medicationId": 4, "medicationName": "Paracetamol", "strength": "500 mg", "dosagePerDose": 1, "quantityUnit": "TABLET",
  "scheduleId": 7, "scheduledTime": "08:00:00", "scheduledDate": "2026-11-03", "status": "PENDING",
  "logId": null, "takenAt": null, "remainingQuantity": 20, "lowStock": false }
```
After `POST /api/medication-logs/7/taken`: `status: "TAKEN"`, `logId: 12`, `takenAt: "2026-11-03T08:05:10"`, `remainingQuantity: 19`.
After `DELETE /api/medication-logs/12`: back to `status: "PENDING"`, `logId: null`, `remainingQuantity: 20`.

Insufficient stock (409): `{ "status": 409, "message": "Not enough stock: this dose needs 1.00 but only 0.00 left" }`

## Summary
`GET /api/medication-logs/summary?date=2026-11-03`
```json
{ "date": "2026-11-03", "totalDoses": 4, "takenDoses": 2, "pendingDoses": 2, "completionPercent": 50 }
```
`pendingDoses` = not confirmed. It is never called "missed".

## Changed behaviour from Day 3
- `DELETE /medications/{id}/schedules/{scheduleId}` is now a **soft delete** (still 204). Removed schedules disappear from lists
  and stop generating doses, but their logs are kept. Adding the same time again re-activates the old schedule (same id).
- `PUT /medications/{id}/schedules/{scheduleId}`: if the schedule already has dose history, the old schedule is retired and the new
  time becomes a separate schedule, so history keeps its original time. **The returned `id` may therefore differ.**
- A lock timeout/deadlock between two simultaneous stock changes returns 409 "Another update is in progress, please try again".

---
# Day 4b – Blister strips (one compartment = one physical tablet)

Tablets and capsules are tracked **tablet by tablet**. Each medicine has ONE strip; every compartment is a row in
`medication_tablets` (slot number, AVAILABLE/TAKEN, date+time taken). Stock (`remaining`) always equals the number of AVAILABLE
compartments. Syrup (ml) has no strip and keeps the dose endpoints from Day 4 (`/medication-logs/...`).

| Method | Path | Who | Success | Errors |
|---|---|---|---|---|
| GET | /strips | parent: own; child: `?parentId=` of an approved parent | 200 list of strips | 400 child without parentId, 403 not approved |
| POST | /tablets/{tabletId}/take | PARENT | 200 updated strip | 404 not yours / unknown, 409 already taken or no stock |
| POST | /tablets/{tabletId}/undo | PARENT | 200 updated strip | 404, 409 not currently taken |

## Strip
```json
{ "medicationId": 4, "medicationName": "Paracetamol", "strength": "500 mg", "type": "TABLET", "unit": "TABLET",
  "dosagePerDose": 1, "totalTablets": 12, "takenTablets": 1, "remainingTablets": 11,
  "lowStockThreshold": 3, "lowStock": false, "scheduleTimes": ["08:00:00", "20:00:00"],
  "expectedToday": 2, "takenToday": 1,
  "tablets": [ { "id": 41, "slotNumber": 1, "status": "TAKEN", "takenAt": "2026-11-03T08:07:00" },
               { "id": 42, "slotNumber": 2, "status": "AVAILABLE", "takenAt": null } ] }
```
`expectedToday` = dose times x tablets per dose. It is informational: an unconfirmed tablet is never "missed".

## Behaviour
- **Take**: marks that exact compartment TAKEN with the server time and removes one unit of stock, in one transaction
  (stock row locked first). Taking the same compartment twice returns 409.
- **Undo**: makes that exact compartment AVAILABLE again and gives one unit back. Undo twice returns 409.
- **Create a medicine** (`POST /medications`) with `initialQuantity: 12` creates 12 compartments. Tablet/capsule counts must be whole numbers, max 200.
- **Update stock** (`PUT /medications/{id}/inventory`) for tablets/capsules means "tablets now in the box":
  raising it adds empty compartments after the last slot, lowering it removes the highest-numbered AVAILABLE ones.
  Compartments already TAKEN are never removed. A strip holds at most 200 compartments including taken ones.
- A tablet/capsule cannot be switched to syrup (or back) by `PUT /medications/{id}` (400).

## Changed behaviour from Day 4
- `/medication-logs/**` now only handles **syrup**. `POST /medication-logs/{scheduleId}/taken` and `DELETE /medication-logs/{logId}` return 400 for tablets/capsules;
  `/medication-logs/today` and `/summary` no longer list them. Old tablet dose logs stay in the database but are not shown.

---
# Day 5 – Daily well-being check-in and medication adherence summary

Both features follow the same rules as Day 3/4: the user is always taken from the JWT (never from the request body),
the service layer enforces ownership and approval, and a parent's own data is the default.

## Daily well-being check-in

One check-in per parent per date. Saving a date that already has a check-in **updates it** (same row, same `id`); a second row is
never created (also enforced by `UNIQUE(parent_id, checkin_date)` in the database).

| Method | Path | Who | Success | Errors |
|---|---|---|---|---|
| POST | /checkins | PARENT | 201 created, or 200 when an existing check-in for that date was updated | 400 validation / future date / old-style feeling, 403 child |
| GET | /checkins?limit= | parent: own. Child: add `parentId=` | 200 list, newest first (`limit` default 30, 1-100) | 400 bad `limit` or child without `parentId`, 403 see below |
| GET | /checkins/{date} | same as above | 200 one check-in | 404 no check-in that day, 400 bad date, 403 |

There is no edit-by-id or delete endpoint. A child that sends PUT or DELETE to `/checkins/**` is refused with 403.

### Request – `POST /api/checkins`
```json
{ "date": "2026-11-12", "feeling": "GOOD", "energyLevel": "HIGH", "symptoms": "mild cough", "notes": "slept well" }
```
| Field | Rules |
|---|---|
| `date` | required, `yyyy-MM-dd`, not in the future |
| `feeling` | required: `GOOD`, `OKAY` or `NOT_GOOD` |
| `energyLevel` | required: `HIGH`, `MODERATE` or `LOW` |
| `symptoms`, `notes` | optional, at most 500 characters each. Spaces are trimmed; empty text is stored as `null` |

### Response
```json
{ "id": 9, "date": "2026-11-12", "feeling": "GOOD", "energyLevel": "HIGH", "symptoms": "mild cough", "notes": "slept well",
  "createdAt": "2026-11-12T09:15:00", "updatedAt": "2026-11-12T09:15:00" }
```
No user details or password data are ever returned.

Rows saved before Day 5 can still be read: their `feeling` may be an older value (`VERY_GOOD`, `NORMAL`, `FEELING_LOW`, `FEELING_UNWELL`),
their `energyLevel` is `null`, and `updatedAt` falls back to `createdAt`. These old values are refused in **new** check-ins (400).

### Who can read what
| Caller | Result |
|---|---|
| Parent | always their own check-ins. A `parentId` that is sent is ignored, so another parent's data can never be reached |
| Child, connection APPROVED and the parent chose **share check-ins** | 200 |
| Child, connection APPROVED but sharing is off | 403 `"This parent has not shared their check-ins with you"` |
| Child, connection PENDING, REJECTED or revoked, or not linked | 403 `"You do not have access to this parent's information"` |
| Child without `?parentId=` | 400 |
| Child creating, changing or deleting | 403 |
| No / invalid token | 401 |

`share_checkins` is chosen by the parent when approving a request (`POST /family/approve` with `shareCheckins: true`). Revoking access resets it to false.

## Medication adherence summary

| Method | Path | Who | Success | Errors |
|---|---|---|---|---|
| GET | /adherence/summary?date=YYYY-MM-DD | parent: own. Child: add `parentId=` of an APPROVED parent | 200 summary | 400 future/bad date or child without `parentId`, 403 not approved |

`date` defaults to today. The endpoint is **read-only**: it never marks anything taken or missed, never changes a tablet, a log or the stock.
A child needs only an APPROVED connection for this endpoint (the same rule as medicines and strips). The check-in sharing choice does not apply to it.

### What is counted
- **Unit.** Totals count *confirmations*: one tablet, capsule or syrup dose that the parent confirmed counts once. Each medicine also states its own
  `unit` (`tablets`, `capsules` or `doses`). A syrup dose is one confirmation even though it is e.g. 5 ml.
- **Tablets / capsules** (`medication_tablets`). *Confirmed on a date* = compartments with status `TAKEN` whose `taken_at` falls on that date.
  *Expected today* = tablets per dose x number of active dose times, rounded up (e.g. 2 tablets x 2 times = 4). A compartment is **not** assumed to be one dose.
- **Syrup** (`medication_logs`). *Confirmed* = `TAKEN` logs for that date. *Expected today* = one dose per active schedule.
- **No double counting.** Tablet medicines are counted only from `medication_tablets`; syrup only from `medication_logs`. Old log rows of tablet medicines are ignored.
- **Only active medicines** appear, and only those that are due today (inside their start/end dates) or have confirmations that day.
- **Physical stock** (`physicalStock`) is what is left in the box. It is reported separately and never added to, or derived from, the confirmed counts.

### Today
`GET /api/adherence/summary`
```json
{ "date": "2026-11-12", "today": true, "expectedAvailable": true, "message": null,
  "confirmedTotal": 3, "expectedTotal": 4, "remainingTotal": 1, "completionPercent": 75, "extraConfirmations": 0,
  "medications": [
    { "medicationId": 4, "name": "Paracetamol", "strength": "500 mg", "type": "TABLET", "unit": "tablets",
      "dosagePerDose": 1, "dosageUnit": "TABLET", "timesPerDay": 2,
      "confirmed": 1, "expected": 2, "remaining": 1, "completionPercent": 50, "extraConfirmations": 0,
      "physicalStock": { "remaining": 11, "unit": "TABLET", "lowStock": false } },
    { "medicationId": 7, "name": "Cough syrup", "strength": null, "type": "SYRUP", "unit": "doses",
      "dosagePerDose": 5, "dosageUnit": "ML", "timesPerDay": 2,
      "confirmed": 2, "expected": 2, "remaining": 0, "completionPercent": 100, "extraConfirmations": 0,
      "physicalStock": { "remaining": 90, "unit": "ML", "lowStock": false } } ] }
```
- `remaining` = `expected` - counted confirmations. It is never negative.
- `completionPercent` = counted / expected, a whole number, never above 100. It is `null` when nothing is expected.
- **Extra confirmations.** If more is confirmed than expected (e.g. 3 tablets confirmed, 2 expected) the real number stays in `confirmed`, `remaining` is 0,
  the percentage stays 100 and the surplus is reported in `extraConfirmations`. A medicine's surplus never hides another medicine's pending dose:
  the overall percentage uses each medicine's counted amount, capped at its own expected amount.
- A `TAKEN` syrup log whose schedule was removed later is still a real confirmation (`confirmed`), but it is not one of today's doses, so it is counted as an extra and does not complete an active dose.

### Previous dates
`GET /api/adherence/summary?date=2026-11-10`
```json
{ "date": "2026-11-10", "today": false, "expectedAvailable": false,
  "message": "Expected doses and completion are only shown for today. CareConnect does not keep a history of past schedules, so today's schedule cannot be used to judge what was due on an earlier day. This page shows only what was actually confirmed on that date.",
  "confirmedTotal": 3, "expectedTotal": null, "remainingTotal": null, "completionPercent": null, "extraConfirmations": null,
  "medications": [
    { "medicationId": 4, "name": "Paracetamol", "strength": "500 mg", "type": "TABLET", "unit": "tablets",
      "dosagePerDose": 1, "dosageUnit": "TABLET", "timesPerDay": null,
      "confirmed": 3, "expected": null, "remaining": null, "completionPercent": null, "extraConfirmations": null,
      "physicalStock": { "remaining": 11, "unit": "TABLET", "lowStock": false } } ] }
```
**Limitations (by design).**
- Schedules and dosages are edited in place and have no history, so what was *due* on an earlier day cannot be known. For a past date the API returns only what was
  confirmed (`expected`, `remaining`, `completionPercent` and `extraConfirmations` are `null`; `message` explains why). Only medicines with confirmations that day are listed.
- Tablets are dated by when they were **confirmed** (`taken_at`). Syrup doses are dated by the **day the dose was for** (`scheduled_date`), which can differ if a past dose was confirmed later.
- Tablets marked taken before Day 4b have no `taken_at` time and cannot be assigned to a date, so they are not counted.
- `physicalStock` is always the stock **now**, not on the requested date (stock history is not stored).
- Medicines that were removed (soft-deleted) are not shown, on any date.
- The summary's expected value respects a medicine's start and end date. The strip header's `expectedToday` (Day 4b) does not, so the two can differ for a medicine that has not started or has ended.

### Errors
```json
{ "status": 400, "message": "Date cannot be in the future" }
{ "status": 403, "message": "You do not have access to this parent's information" }
```
