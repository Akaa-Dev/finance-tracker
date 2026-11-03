# Finance Tracker

Private, single-owner finance tracker. **Phase 3:** deterministic daily cash forecasts, itemized future income/expenses and remaining installment dues, monthly rollups and read-only what-if scenarios, alongside Phase 1–2 actual records and reconciliation. Authentication and ownership are enforced. Dashboard debt summaries remain labeled placeholders. No bank connection, analytics, or seeded finance data.

## Prerequisites

- JDK **25**; the wrapper downloads **Gradle 9.8.2**, Groovy build scripts.
- **Node 26.13.0**, latest stable confirmed against the [official index](https://nodejs.org/dist/index.json) on 2026-11-03; pinned in `.nvmrc`.
- Running, unpaused Docker Desktop/Engine with Compose.
- Internet access for first dependency/image/browser downloads.

The explicit user stack overrides the starter defaults: **Spring Boot 4.1.2**, Java 25 and Gradle 9.8.2. [Spring requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) support this combination. React 19, TypeScript, Vite 7, Tailwind 4, React Router 7 and TanStack Query 5 are locked in `frontend/package-lock.json`. PostgreSQL uses the maintained `17` image tag; pin an approved digest for deployment.

## Development startup

From the repository root:

```sh
cp .env.example .env
```

Edit `.env` with `DB_PASSWORD`, your `OWNER_EMAIL`, and a unique `OWNER_PASSWORD` (at least 16 characters, at most 72 UTF-8 bytes). Use shell-compatible assignments and quote special characters. No default owner credentials are supplied; never commit this file.

```sh
docker compose --env-file .env -f infrastructure/compose.yaml up -d --wait
set -a
. ./.env
set +a
cd backend
./gradlew bootRun
```

In a second terminal, from the root, select the pinned Node version (`nvm install && nvm use` if using nvm), then:

```sh
cd frontend
npm ci
npm run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173) and sign in. Use this hostname consistently. Vite proxies the API to loopback port 8080 in the current configuration; set `VITE_API_PROXY` if your backend uses another port, and restart Vite after changing the target. The browser Network panel shows `/api` on the Vite origin because forwarding happens server-side. Restart the backend after code changes: an older process can accept login while returning an outdated Forecast response. PostgreSQL binds to loopback port 5432. Liquibase applies migrations and Hibernate validates mappings.

1. In **Settings**, add a cash account with its actual opening balance/date. This displays its opening position; Transactions shows the derived as-of balance.
2. In **Installments**, create a plan with fixed total payable, count and first due date. Schedules keep the anchor day through shorter months and put the cents remainder in the final due.
3. Open the plan and record actual payments against the cash account. Partial/early payments allocate oldest unpaid dues first. No ordinary expense transaction is created.
4. Review derived totals, overdue amounts, schedule and history. **Void payment** requires confirmation and retains the original record/allocations.
5. Add INCOME/EXPENSE categories in **Settings**, then record ordinary actuals in **Transactions**. Do not enter an installment repayment as an ordinary expense or an own-account transfer as income.
6. Choose account/date filters to see period income, ordinary expenses, installment repayments and net movement separately. Cash as of the end date includes the opening position and all eligible valid history, not only the period's start. Category/direction/record-state filters affect the record list, not the summary.
7. Create an expected income or expense with a category and daily, weekly, monthly, yearly or one-off schedule on **Forecast**. Recurring entries become rules; one-offs become planned events. Neither changes actual cash. You can explicitly generate occurrence dates and link an actual to its source in Transactions to mark it realized without another movement. Optional source account/category/direction must match; actual amount/date may differ from the nominal expectation.
8. Corrections require a reason, void the original and append a replacement atomically, transferring compatible source links. Voiding an actual reopens its source; unlinking retains the actual cash movement and reopens the source. All link history is retained. Rule financial fields and planned-event financial fields are immutable; rules may be paused and unrealized planned events cancelled/reopened.
9. Open **Forecast** to choose an inclusive range, inspect exact daily/monthly cash and remaining installment debt, see a month-by-month graph and current actual balance for each cash account, and compare one hypothetical expense, new installment schedule or recurring income reduction. A simulation writes no financial records. Link received income or paid expenses to their sources to suppress realized expectations. The graph combines all cash accounts and unassigned expectations; future dues are projected repayments, not posted payments. Forecasts are nominal projections from manual entries, not bank-confirmed predictions.

No plan/account editing or deletion is exposed. Ordinary actual corrections append replacement records instead of overwriting history. Choose opening dates and schedules carefully. Payment dates must be on/after the account opening date and no later than today. Balances may become negative; a sufficient-funds restriction is not imposed.

The owner is initialized once at backend startup. Database constraints prevent a second owner. Restarts or environment changes do **not** reset its stored credentials. Password change/recovery is not implemented. Sessions expire after 30 minutes of inactivity and on backend restart. Credentials and financial records are not persisted in browser storage. CSRF uses fresh session tokens for mutations; cookies are HTTP-only and SameSite=Strict. `COOKIE_SECURE=false` is for loopback HTTP only; an HTTPS deployment requires `true`, TLS, same-origin frontend/API routing and deployment hardening.

Stop without deleting records:

```sh
docker compose --env-file .env -f infrastructure/compose.yaml down
```

The named volume preserves data. Do not use `down -v` against data you need. Backup/recovery remains Phase 4 work.

## Build and test

```sh
cd backend
./gradlew test bootJar
# Docker-independent calculation/bootstrap tests:
./gradlew test --tests '*FixedScheduleTest' --tests '*OwnerBootstrapTest'
cd ../frontend
npm ci
npm run build
```

PostgreSQL integration tests start disposable Testcontainers databases and never use `.env`. They fail visibly if Docker is unavailable. Tests cover auth/CSRF, migrations, precise schedules/allocation, ownership, idempotency, concurrent payment/actual posting, concurrent realization, correction rollback, opening/as-of boundaries, recurrence anchors and source uniqueness. All cash reads exclude planned definitions and sum installment payments from their separate source exactly once.

Browser acceptance uses a separate disposable database with generated credentials, without reading `.env`. It requires Node, Java 25, Docker, Python 3, curl and free loopback ports 18080/15173:

```sh
cd frontend
npx playwright install chromium
cd ..
./infrastructure/verify-browser.sh
```

The script builds/starts a temporary backend, runs Playwright desktop and 360px checks, and cleans up only its own processes/container. Vite's test API target is set with `VITE_API_PROXY`; this value is an address, never a secret. The script never binds or replaces your normal PostgreSQL service. Screenshots are generated under ignored `frontend/test-results/`. No Firefox/WebKit or complete screen-reader audit has been performed. The isolated backend disables optional local configuration imports with `SPRING_CONFIG_IMPORT=` so local `.env` files are not used.

## Contracts and verification

- [Phase 3 forecast API](docs/PHASE_3_API.md)
- [Phase 3 verification](docs/PHASE_3_VERIFICATION.md)
- [Phase 2 API examples](docs/PHASE_2_API.md)
- [Phase 1 API examples](docs/PHASE_1_API.md)
- [Authentication API and curl sign-in](docs/PHASE_0_API.md)
- [Phase 0 verification and resolved blockers](docs/PHASE_0_VERIFICATION.md)
- [Phase 1 verification, results and limitations](docs/PHASE_1_VERIFICATION.md)
- [Phase 2 verification and limitations](docs/PHASE_2_VERIFICATION.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Ready-to-paste Phase 4 prompt](docs/NEXT_PHASE_4_PROMPT.md)

`backend/` contains authentication, accounts, installments, ordinary actuals, recurrence/planned reconciliation and read-only forecast APIs/services/JPA mappings. `frontend/` contains the shell and financial forms/views. `infrastructure/` contains local PostgreSQL and isolated browser verification. `docs/` contains the source product/domain contracts and milestone evidence. The follow-up `004-recurring-frequency` migration expands valid recurring schedules to daily and weekly; forecasts still derive values from owner-scoped records without storing balances.

Payment posting locks the owner then plan in a transaction. Each request requires an owner-scoped UUID `Idempotency-Key`; identical normalized retries return the **original stored receipt**, including after later payments or a void. Changed reuse returns 409. Read plan detail for current balances. The UI retains the key for unchanged retries while the plan page remains open; after navigation/reload, inspect history before submitting a possibly interrupted payment again. No automatic transfers exist. Phase 2 actual/rule/planned creation, correction and explicit linking use their own shared owner-scoped idempotency receipt table. UI keys persist only while the relevant form/page is mounted; inspect history after navigation/reload before resubmitting an interrupted mutation. Phase 3 scenarios are read-only calculations and require no posting key.

Remaining work: password recovery/rate limiting, backup/restore and production deployment hardening (Phase 4). Optional plan tags and summary/upcoming aggregate endpoints are not exposed. No balances are bank-confirmed.


Phase 2's €0 opening + €3,500 salary − €1,300 rent − €80 installment acceptance case is covered by PostgreSQL and browser tests and yields **€2,120**, with only two ordinary actual records. No demo records are seeded into your development database. As-of balances use currently valid history; subsequent voids/corrections restate earlier-date results rather than providing archival historical snapshots.

If Forecast briefly loads then disappears, stop the running backend in its terminal and start `./gradlew bootRun` again from `backend/`; refresh the browser. A process started before the latest forecast build cannot return the cash/debt fields expected by the current frontend. The page now reports this mismatch instead of blanking. If the backend port differs from 8080, start Vite with `VITE_API_PROXY=http://127.0.0.1:<port> npm run dev` from `frontend/`. The normal Compose instructions still require an explicit root `--env-file .env`; do not place secrets in frontend `VITE_` variables.
