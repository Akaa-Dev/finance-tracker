# Finance Tracker

Private, single-owner finance tracker. **Phase 1:** cash account setup, fixed monthly installment plans, exact-cent schedules, partial/advance payments, payment history and auditable voids. Session authentication and owner-scoped records are enforced. Dashboard summaries, ordinary income/expenses, recurrences and forecasts remain labeled placeholders. No bank connection, analytics, or seeded finance data.

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

Open [http://127.0.0.1:5173](http://127.0.0.1:5173) and sign in. Use this hostname consistently. Vite proxies the API to loopback port 8080; no cross-origin cookie configuration is needed. PostgreSQL binds to loopback port 5432. Liquibase applies migrations and Hibernate validates mappings.

1. In **Settings**, add a cash account with its actual opening balance/date. This displays the opening position, not a current bank balance.
2. In **Installments**, create a plan with fixed total payable, count and first due date. Schedules keep the anchor day through shorter months and put the cents remainder in the final due.
3. Open the plan and record actual payments against the cash account. Partial/early payments allocate oldest unpaid dues first. No ordinary expense transaction is created.
4. Review derived totals, overdue amounts, schedule and history. **Void payment** requires confirmation and retains the original record/allocations.

No plan/account editing or deletion is exposed. Choose opening dates and schedules carefully. Payment dates must be on/after the account opening date and no later than today. Balances may become negative; sufficient account funds are not inferred in Phase 1.

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

PostgreSQL integration tests start disposable Testcontainers databases and never use `.env`. They fail visibly if Docker is unavailable. Tests cover auth/CSRF, migrations, precise schedules, allocation, validation, ownership/reference rejection, idempotency, concurrent payment posting, derived statuses and void recomputation.

Browser acceptance uses a separate disposable database with generated credentials, without reading `.env`. It requires Node, Java 25, Docker, Python 3, curl and free loopback ports 18080/15173:

```sh
cd frontend
npx playwright install chromium
cd ..
./infrastructure/verify-browser.sh
```

The script builds/starts a temporary backend, runs Playwright desktop and 360px checks, and cleans up only its own processes/container. Vite's test API target is set with `VITE_API_PROXY`; this value is an address, never a secret. The script never binds or replaces your normal PostgreSQL service. Screenshots are generated under ignored `frontend/test-results/`. No Firefox/WebKit or complete screen-reader audit has been performed.

## Contracts and verification

- [Phase 1 API examples](docs/PHASE_1_API.md)
- [Authentication API and curl sign-in](docs/PHASE_0_API.md)
- [Phase 0 verification and resolved blockers](docs/PHASE_0_VERIFICATION.md)
- [Phase 1 verification, results and limitations](docs/PHASE_1_VERIFICATION.md)
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Ready-to-paste Phase 2 prompt](docs/NEXT_PHASE_2_PROMPT.md)

`backend/` contains authentication, account and installment APIs/services/domain calculations/JPA mappings. `frontend/` contains the shell and financial forms/views. `infrastructure/` contains local PostgreSQL and isolated browser verification. `docs/` contains the source product/domain contracts and milestone evidence.

Payment posting locks the owner then plan in a transaction. Each request requires an owner-scoped UUID `Idempotency-Key`; identical normalized retries return the **original stored receipt**, including after later payments or a void. Changed reuse returns 409. Read plan detail for current balances. The UI retains the key for unchanged retries while the plan page remains open; after navigation/reload, inspect history before submitting a possibly interrupted payment again. No hypothetical scenarios or automatic transfers exist.

Remaining work: ordinary transactions/current cash balances/recurrences (Phase 2), forecasts (Phase 3), password recovery/rate limiting, backup/restore and production deployment hardening (Phase 4). Optional plan tags and summary/upcoming aggregate endpoints are not exposed. No balances are bank-confirmed.
