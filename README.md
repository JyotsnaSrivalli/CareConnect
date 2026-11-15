# CareConnect
Medication management platform where elderly parents track their medicines and their children remotely monitor adherence and stock.

*(Full README is written on Day 6.)*

## Quick start
1. Run `database/schema.sql` in MySQL Workbench.
2. Backend: copy `backend/src/main/resources/application-local.properties.example` to `application-local.properties`, fill in values, then `cd backend && mvn spring-boot:run`
3. Frontend: `cd frontend && npm install && npm run dev`
4. Open http://localhost:5173
