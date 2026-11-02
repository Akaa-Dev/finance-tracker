# Codex delivery plan

Work milestone-by-milestone, commit/checkpoint changes after each passing milestone if Git is available. Don't mark untested tasks as complete. Confirm versions and development commands in README.

## Phase 0 — repository/bootstrap
- [x] Create monorepo `/backend`, `/frontend`, `/infrastructure`, `/docs` and root README.
- [ ] Initialize Spring Boot 4.1.2, Gradle 9.8.2 (Groovy), Java 25, PostgreSQL, Liquibase, Spring Security, validation, Spring Data JPA and health endpoint. Code added; runtime verification pending.
- [x] Initialize React/TypeScript/Vite, router, TanStack Query, Tailwind, and responsive shell/navigation.
- [x] Add Docker Compose for PostgreSQL and developer run instructions, `.env.example`, `.gitignore`.
- [ ] Implement minimal secured owner bootstrap/auth flow; no default admin password committed. Code and tests added; PostgreSQL integration/sign-in verification pending.
- [ ] Verify backend, frontend, migration and health endpoint launch; record exact commands/output.

**Acceptance:** Fresh clone with documented prerequisites can start Postgres, backend and frontend; a user can authenticate and see an empty dashboard; unauthenticated financial API requests are denied.

Phase 0 status: implementation added, acceptance not yet verified. Explicit user stack override: Java 25, Spring Boot 4.1.2, Groovy Gradle 9.8.2, latest stable Node (26.13.0 at implementation). See `PHASE_0_VERIFICATION.md` for exact checks and limitations. No financial tables/endpoints are claimed complete.

## Phase 1 — installment vertical slice (highest priority)
- [ ] Liquibase plan/due/payment/allocation/account tables with constraints/indexes.
- [ ] Create-plan service generates correct monthly dues and handles cents/short months.
- [ ] Create GET list/detail/schedule and POST payment endpoints with validation and ownership.
- [ ] Allocate partial and advance payments oldest-due-first, under concurrency lock and idempotency key.
- [ ] Derive outstanding balance and due status from valid financial records.
- [ ] React plan list/detail/create form, payment modal and progress component.
- [ ] Unit tests plus PostgreSQL integration tests for listed invariant cases.

**Acceptance:** Create a €1,200/6-month plan, pay €80, observe outstanding €1,120 and first due remaining €120. Pay another €120 and observe first due zero. Show a human-readable transaction history, no double entry.

## Phase 2 — income and expenses
- [ ] Cash account balances and account selection; category and actual transaction CRUD with reversals.
- [ ] Recurrence rules and future planned one-offs with reconciliation/realization linkage.
- [ ] Transaction UI, summary of income, ordinary expenses, installment repayments and balances.
- [ ] Tests for ownership, voids, opening balances and recurrence anchor dates.

**Acceptance:** Add €3,500 salary, €1,300 rent, €80 installment payment; app reports cash movement without counting €80 twice, assuming transactions and account opening values are defined.

## Phase 3 — forecasting and scenario engine
- [ ] Daily deterministic event projection from actual balances, future recurring/one-off cash flows, remaining installment dues.
- [ ] Handle overdue, partially paid, actual-reconciled recurring and planned realized events.
- [ ] Monthly rollups, lowest daily balance, baseline vs scenario deltas.
- [ ] Forecast and what-if UI including mobile-friendly tables/charts.
- [ ] Deterministic tests for boundary dates and double counting.

**Acceptance:** Forecast explicitly itemizes sources; altering a hypothetical future installment changes only simulation output, not stored plan/payment/transaction records.

## Phase 4 — hardening
- [ ] Session/CSRF and permission review, rate limiting where needed, errors/validation and logging hygiene.
- [ ] Accessibility/mobile QA at 360px and desktop, loading/error/empty states.
- [ ] CSV export, backup/recovery instructions, user-facing limitations and data quality warning.
- [ ] CI runs backend tests and frontend lint/build; documented local and Docker deployment.

**Acceptance:** Fresh install and automated test suite pass; backup/restore is documented and verified if environment permits; project contains no secrets or real financial information.

## What Codex must report at the end of each milestone
Implemented files, exposed endpoints and screenshots or descriptions, commands actually run and outcomes, remaining limitations, and exact next task. Do not invent verification results.
