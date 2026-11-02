# Codex repository instructions — Personal Finance & Installment Tracker

## Mission
Build a production-minded, private, responsive personal finance web application for tracking installments, partial repayments, income, expenses, and future cash flow. The source of truth is `docs/PRODUCT_SPEC.md`, with domain and technical details in `docs/ARCHITECTURE.md` and `docs/DATA_MODEL.md`. Follow the staged implementation and acceptance criteria in `docs/IMPLEMENTATION_PLAN.md`.

## Stack and conventions
- Backend: Java 21 LTS, Spring Boot 3.x (choose a supported patch), Maven, Spring Web, Validation, Security, Spring Data JPA, Liquibase, PostgreSQL. Check current supported versions when provisioning; avoid prerelease versions.
- Frontend: React, TypeScript, Vite, React Router, TanStack Query, Tailwind CSS; use accessible components and responsive layouts.
- Monorepo: `backend/`, `frontend/`, `infrastructure/`, `docs/`.
- Backend modules: `auth`, `account`, `installment`, `transaction`, `recurrence`, `forecast`, `dashboard`, `common`.
- Single-user MVP via authenticated application user, but every financial record is owner-scoped. EUR only initially; no multi-currency arithmetic.
- Keep the deployment simple: local Docker Compose, no Kubernetes or microservices for MVP.

## Non-negotiable financial correctness
- Use Java `BigDecimal` and PostgreSQL `NUMERIC(19,2)` for money, never floating point. Store ISO dates as `LocalDate`; timestamps as `Instant`.
- Separate **obligations (installment dues)** from **actual payments** and **payment allocations**.
- An installment plan's outstanding principal/contractual payable is derived from total payable minus all **valid** payments. Never maintain a mutable balance as the only source of truth.
- One payment can be allocated across multiple dues; a due may have many partial allocations. Allocate oldest remaining due first (including overdue dues).
- Never accept overpayments, over-allocations, duplicate submissions, or cross-owner/cross-plan references. Lock the plan row while posting a payment and use a request idempotency key.
- For v1, use fixed-total, interest-free or fixed-finance-charge plans only. No variable interest, loan amortization, currency conversion, payment penalties, or changing payment schedules after posting payments.
- Transactions for income/ordinary expenses and installment payments are disjoint sources for cash movement. NEVER duplicate installment payments as expense transactions.
- Forecasts should use posted actual payments to reduce pending obligations, reconcile current-period recurring rules against actuals, and avoid counting past cash movements again.
- A simulation is read-only and must never persist hypothetical scenarios as real transactions.
- Financial corrections should be auditable; prefer reversing/voiding over destroying paid history.

## Engineering workflow
- Before each phase, inspect existing files and summarize the plan. Implement only the current milestone; do not build future phases prematurely.
- Create tests for the financial invariants, including cents rounding, partial payments, overpayment, payment allocation, idempotency, concurrent payments, and forecast double-counting.
- Use integration tests with Testcontainers PostgreSQL when DB behavior matters. Unit tests for deterministic domain calculations.
- Restrict routes by authenticated ownership, validate request payloads, never log financial secrets or credentials, and return consistent Problem Details errors.
- Keep secrets in environment variables, provide `.env.example` without secrets, and never commit actual personal financial data.
- After each milestone, run relevant tests/builds (when available), record exact results, and update `docs/IMPLEMENTATION_PLAN.md` checkboxes.
- If a toolchain or Docker is unavailable, report the unverified step explicitly rather than claiming success.
- Avoid gratuitous dependencies, unnecessary abstractions, and placeholder endpoints represented as completed features.

## Definition of done for each milestone
Working code, migration(s), tests, API examples, README run instructions, and clear notes on assumptions/limitations. Favor an end-to-end working vertical slice over scaffolding all modules at once.
