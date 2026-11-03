# Codex delivery plan

Work milestone-by-milestone, commit/checkpoint changes after each passing milestone if Git is available. Don't mark untested tasks as complete. Confirm versions and development commands in README.

## Phase 0 — repository/bootstrap
- [x] Create monorepo `/backend`, `/frontend`, `/infrastructure`, `/docs` and root README.
- [x] Initialize Spring Boot 4.1.2, Gradle 9.8.2 (Groovy), Java 25, PostgreSQL, Liquibase, Spring Security, validation, Spring Data JPA and health endpoint.
- [x] Initialize React/TypeScript/Vite, router, TanStack Query, Tailwind, and responsive shell/navigation.
- [x] Add Docker Compose for PostgreSQL and developer run instructions, `.env.example`, `.gitignore`.
- [x] Implement minimal secured owner bootstrap/auth flow; no default admin password committed.
- [x] Verify backend, frontend, migration and health endpoint launch; record exact commands/output.

**Acceptance:** Fresh clone with documented prerequisites can start Postgres, backend and frontend; a user can authenticate and see an empty dashboard; unauthenticated financial API requests are denied.

Phase 0 accepted on 2026-11-03: PostgreSQL migration/health and authentication integration tests pass; desktop and 360px browser sign-in/empty dashboard/sign-out pass. Compose startup also verified in an isolated project. Explicit user stack override remains Java 25, Spring Boot 4.1.2, Groovy Gradle 9.8.2 and latest stable Node (26.13.0 checked on 2026-11-03). See `PHASE_0_VERIFICATION.md` and `PHASE_1_VERIFICATION.md` for evidence.

## Phase 1 — installment vertical slice (highest priority)
- [x] Liquibase plan/due/payment/allocation/account tables with constraints/indexes.
- [x] Create-plan service generates correct monthly dues and handles cents/short months.
- [x] Create GET list/detail/schedule and POST payment endpoints with validation and ownership.
- [x] Allocate partial and advance payments oldest-due-first, under concurrency lock and idempotency key.
- [x] Derive outstanding balance and due status from valid financial records.
- [x] React plan list/detail/create form, payment modal and progress component.
- [x] Unit tests plus PostgreSQL integration tests for listed invariant cases.

**Acceptance:** Create a €1,200/6-month plan, pay €80, observe outstanding €1,120 and first due remaining €120. Pay another €120 and observe first due zero. Show a human-readable transaction history, no double entry.

Phase 1 accepted on 2026-11-03. Account setup, immutable schedules, plan list/search/detail, partial/advance payments, idempotency, row locks, derived statuses, history and confirmed voids are implemented. No ordinary transaction/recurrence/forecast functionality is claimed. See `PHASE_1_API.md` and `PHASE_1_VERIFICATION.md`.

## Phase 2 — income and expenses
- [x] Cash account balances and account selection; category and actual transaction CRUD with reversals.
- [x] Recurrence rules and future planned one-offs with reconciliation/realization linkage.
- [x] Transaction UI, summary of income, ordinary expenses, installment repayments and balances.
- [x] Tests for ownership, voids, opening balances and recurrence anchor dates.

**Acceptance:** Add €3,500 salary, €1,300 rent, €80 installment payment; app reports cash movement without counting €80 twice, assuming transactions and account opening values are defined.

Phase 2 accepted on 2026-11-03. Owner-scoped ordinary actuals, append-only corrections, reasoned voids, exact as-of balances/period summaries, immutable monthly/yearly rules, explicit occurrence generation and auditable one-to-one source reconciliation are implemented. The EUR 3,500 − EUR 1,300 − EUR 80 acceptance result is EUR 2,120 without an installment expense duplicate. See `PHASE_2_API.md` and `PHASE_2_VERIFICATION.md`. No forecasts/scenarios are implemented.

## Phase 3 — forecasting and scenario engine
- [x] Daily deterministic event projection from actual balances, future recurring/one-off cash flows, remaining installment dues.
- [x] Handle overdue, partially paid, actual-reconciled recurring and planned realized events.
- [x] Monthly rollups, lowest daily balance, baseline vs scenario deltas.
- [x] Forecast and what-if UI including mobile-friendly tables/charts.
- [x] Deterministic tests for boundary dates and double counting.

**Acceptance:** Forecast explicitly itemizes sources; altering a hypothetical future installment changes only simulation output, not stored plan/payment/transaction records.

Phase 3 accepted on 2026-11-03 against the disposable PostgreSQL/browser environments: source-itemized daily/monthly forecasts, realized-source suppression, partial/overdue due handling, and read-only what-if comparisons are implemented. The final clean backend suite passed 35 tests; the final desktop and 360px Chromium suite passed 6 cases. Hypothetical installment tests assert changed results and no stored plan/payment/transaction rows. See `PHASE_3_API.md` and `PHASE_3_VERIFICATION.md`. Phase 4 remains unchecked.

Phase 3 follow-up on 2026-11-03: both income and expense expectations can now be entered from Forecast with a selected descriptive category and DAILY, WEEKLY, MONTHLY, YEARLY or ONE_OFF schedule. Migration `004-recurring-frequency` expands the recurring constraint without changing existing rows. The graph shows combined cash across all accounts and derived remaining installment debt by month, with each account's current actual balance alongside. The clean backend suite passed 38 tests and desktop/360px Chromium passed 6 cases. These are Phase 3 extensions; Phase 4 remains unchecked.

## Phase 4 — hardening
- [ ] Session/CSRF and permission review, rate limiting where needed, errors/validation and logging hygiene.
- [ ] Accessibility/mobile QA at 360px and desktop, loading/error/empty states.
- [ ] CSV export, backup/recovery instructions, user-facing limitations and data quality warning.
- [ ] CI runs backend tests and frontend lint/build; documented local and Docker deployment.

**Acceptance:** Fresh install and automated test suite pass; backup/restore is documented and verified if environment permits; project contains no secrets or real financial information.

## What Codex must report at the end of each milestone
Implemented files, exposed endpoints and screenshots or descriptions, commands actually run and outcomes, remaining limitations, and exact next task. Do not invent verification results.
