# Development Checklist

## Day 1-4: DONE
## Day 4b – Blister strip, one compartment per physical tablet
- [x] Migration day4b_tablet_strips.sql
- [x] MedicationTablet entity/repository, TabletService (take/undo with locks, resize on stock change)
- [x] GET /api/strips, POST /api/tablets/{id}/take, POST /api/tablets/{id}/undo
- [x] Dose endpoints limited to syrup (so stock can't change without a compartment)
- [x] React: TabletStrip / TabletSlot / BlisterStrips (parent clickable, child read-only)
- [x] Unit tests (TabletServiceTest + updated service tests), component tests, Postman 144 requests
- [ ] YOU: run migration, `mvn test`, Postman, browser checks
## Day 5 – Daily check-in + adherence summary
- [x] Migration day5_checkins_migration.sql (additive; reuses `daily_checkins`)
- [x] Check-in backend: DailyCheckin entity, CheckinService (one per date, parent-only writes, child needs APPROVED + share_checkins), `/api/checkins`
- [x] Adherence summary backend: AdherenceService (read-only), `GET /api/adherence/summary`
- [x] React: AdherenceSummary, CheckinForm, CheckinHistory, ParentCheckins; parent and child dashboards
- [x] Password show/hide button on Login and Register
- [x] Unit tests (CheckinServiceTest, AdherenceServiceTest, FamilyServiceTest), frontend tests (Vitest), Postman requests 170-249
- [ ] YOU: `mvn test`, Postman, browser checks
## Day 6 – Tests, README, screenshots, deploy
