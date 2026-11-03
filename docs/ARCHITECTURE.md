# Architecture and API contract

## System diagram
React/TypeScript SPA -> `/api/v1` Spring Boot modular monolith -> PostgreSQL; Docker Compose for local deployment. API is the sole authority for calculations. Spring modules: `auth`, `account`, `installment`, `transaction`, `recurrence`, `forecast`, `dashboard`, `common`.

## Packages
`com.financetracker.<module>.api`, `.application`, `.domain`, `.persistence` where useful. Modules may expose narrow application-service interfaces. Avoid cyclical dependencies. Map entities to DTOs; never expose JPA entity directly.

## API guidelines
- Prefix `/api/v1`.
- UUID in URL; JSON camelCase; ISO-8601 dates.
- Money: JSON decimal number, validated to 2 places and nonnegative/positive as appropriate. Java BigDecimal.
- Pagination/filtering on transaction and installment list endpoints.
- Errors use RFC 9457 Problem Details; no leaked internal stack traces.
- Protected authenticated endpoints; only access own records.
- Posting a payment requires `Idempotency-Key` header (UUID suggested); replaying an identical key/body returns identical result; conflicting reuse produces 409.

## Endpoint inventory
### Accounts
`GET/POST /accounts`; `GET /accounts/{id}`; `GET /accounts/{id}/balance`.
### Installments
`GET/POST /installments`; `GET /installments/{id}`; `GET /installments/{id}/schedule`; `GET /installments/{id}/payments`; `POST /installments/{id}/payments`; `POST /installments/{id}/payments/{paymentId}/void`; `GET /installments/summary`; `GET /installments/upcoming?from=&to=`.
### Transactions
`GET/POST /transactions`; `GET/PATCH /transactions/{id}`; `POST /transactions/{id}/void` (prefer audit-friendly reversals); `GET/POST /categories`.
### Recurrences and future plans
`GET/POST /recurring-rules`; `PATCH /recurring-rules/{id}`; `GET/POST /planned-cashflows`; `PATCH /planned-cashflows/{id}`.
### Summary and forecast
`GET /dashboard/summary`; `GET /dashboard/monthly?from=&to=`; `GET /forecasts/cashflow?from=&to=`; `POST /forecasts/scenarios`.

## Example request: plan
POST /api/v1/installments
```json
{"name":"Laptop","provider":"Shop","principalAmount":1200.00,"totalPayable":1200.00,"installmentCount":6,"firstDueDate":"2026-11-15","currency":"EUR"}
```
Response: plan UUID, plan fields, totalPaid, outstandingBalance, nextDueDate, schedule.

## Example request: payment
POST /api/v1/installments/{planId}/payments
Idempotency-Key: 60d972c7-dce9-4e90-8e49-72c1f364689b
```json
{"accountId":"<account-uuid>","amount":80.00,"paidOn":"2026-11-10","note":"Partial payment"}
```
Response: payment UUID, amount, allocations (due ID, amount, due remaining), totalPaid, planOutstandingBalance.

## Forecast contract
GET /api/v1/forecasts/cashflow?from=2026-11-01&to=2027-04-30
Response: daily or event-level projected balances, monthly rows with income/normal expenses/debt repayments/end balance, lowestDailyBalance and assumptions. The UI can aggregate on display only when doing so does not alter canonical balance calculations.

POST /api/v1/forecasts/scenarios
```json
{"from":"2026-11-01","months":6,"changes":[{"type":"NEW_INSTALLMENT","amount":150.00,"firstDueDate":"2026-12-01","installmentCount":12},{"type":"ONE_TIME_EXPENSE","amount":500.00,"date":"2027-01-10"}]}
```
Response: baseline + hypothetical monthly and daily results, deltas, minimum cash balances. Reject inconsistent changes.

## Forecast methodology
1. Determine starting balances as of forecast start based on account opening position and posted events through start (including installment payments).
2. Generate future recurring occurrences using explicit day-of-month rules. When anchor day doesn't exist in a month, use last day of month; do not drift the anchor date.
3. Generate pending scheduled dues, subtract allocations of all valid payments. Past-due remaining amounts fall on forecast start as immediate outflow.
4. Only include future *unrealized* one-offs. Reconcile actual transactions to recurring occurrences by explicit occurrence linkage where possible; don't heuristically double count.
5. Aggregate chronologically, including running lowest balance for each day. Monthly values are derived from chronological events; reported net income must not count transfers between own accounts as income.
6. Forecast is a projection, not a bank-verified prediction. No background interest or auto-updates.

Phase 3 implementation derives both baseline and hypothetical results from one owner-scoped, read-only REPEATABLE READ snapshot. It returns daily rows, monthly rows and dated source events. Actuals through today remain separate from future nominal expectations. A future selected start carries prior future expectations into its starting balance, while previously overdue dues are charged on the selected start. Scenario changes are validated and applied in memory; no scenario tables or migrations are needed. See `PHASE_3_API.md` for the implemented request fields and response shape.

## Operational
Compose services for PostgreSQL, backend and frontend (or local frontend dev server). Use Liquibase at boot, PostgreSQL in tests via Testcontainers, actuator health endpoint if configured, environment-based secrets, and backup/recovery documentation. Never put personal financial data in seeded demo records by default.
