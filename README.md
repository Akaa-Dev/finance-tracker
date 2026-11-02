# Finance Tracker

Private, single-owner finance tracker. **Phase 0 foundation:** environment-based owner setup, session sign-in/out, PostgreSQL/Liquibase bootstrap, health endpoint, and responsive React navigation. Dashboard, installments, transactions and forecast views are explicitly labeled placeholders. No financial operations or demo records exist yet.

## Prerequisites

- JDK 25; Gradle wrapper downloads Gradle 9.8.2 (no system Gradle required).
- Node 26.13.0 (latest stable at implementation) and npm (use `node --version` to check your shell).
- Docker Desktop/Engine running and unpaused, with Docker Compose.
- Internet access for initial dependencies and PostgreSQL image.

The requested Java 25 / Groovy Gradle setup supersedes the starter's Java 21 / Maven default. This foundation selects Spring Boot 4.1.2 and Gradle 9.8.2 for their Java 25 support. See [Boot requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) and [Gradle compatibility](https://docs.gradle.org/current/userguide/compatibility.html). React 19, Vite 7, Tailwind 4, React Router 7 and TanStack Query 5 are locked in `frontend/package-lock.json`. PostgreSQL uses the maintained `17` image tag; lock an approved image digest for deployment.

## Local development

From the repository root:

```sh
cp .env.example .env
```

Edit `.env`: choose `DB_PASSWORD`, your `OWNER_EMAIL`, and a unique `OWNER_PASSWORD` (at least 16 characters, at most 72 UTF-8 bytes). No default password is supplied. Use shell-compatible assignments; quote values with spaces/special characters. Keep this file private.

```sh
docker compose --env-file .env -f infrastructure/compose.yaml up -d --wait
set -a
. ./.env
set +a
cd backend
./gradlew bootRun
```

In a second terminal:

```sh
nvm install # optional: if you use nvm, reads .nvmrc
nvm use
cd frontend
npm ci
npm run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173), then sign in with the configured owner credentials. Use this hostname consistently. Vite proxies API requests to `127.0.0.1:8080`, so there is no cross-origin cookie setup. PostgreSQL and backend bind to loopback. Database schema is applied by Liquibase; Hibernate validates it. The owner is created once on startup; simultaneous starts cannot create multiple owners. Restarting or changing environment credentials does **not** reset the stored account. Keep the original password safe; password recovery/editing is outside Phase 0.

Sessions expire after 30 minutes of inactivity and on backend restart. Credentials never enter localStorage. CSRF uses a session token fetched before each mutation. Cookies are HTTP-only and SameSite=Strict. `COOKIE_SECURE=false` is for loopback HTTP development; HTTPS deployment requires `COOKIE_SECURE=true`, a same-origin frontend/API proxy, TLS, and deployment hardening. This is a local development configuration, not an internet deployment recipe.

Stop the database without deleting records:

```sh
docker compose --env-file .env -f infrastructure/compose.yaml down
```

The named volume preserves the owner and schema. Do not use `down -v` against data you need. Backups/recovery and password reset are not implemented in this phase.

## Verification

```sh
cd backend
./gradlew test bootJar
# Bootstrap unit tests only (no Docker):
./gradlew test --tests '*OwnerBootstrapTest'
cd ../frontend
npm ci
npm run build
```

Integration tests require Docker and start a disposable PostgreSQL container, independently of `.env`. They verify Liquibase startup, health, singleton owner enforcement, hashed credentials, unauthorized financial route denial, invalid credentials, CSRF, HTTP-only cookies, login and logout. Docker absence fails the integration suite rather than silently skipping it. Financial invariant tests belong to Phase 1 alongside actual financial services.

For actual commands and results from implementation, see [docs/PHASE_0_VERIFICATION.md](docs/PHASE_0_VERIFICATION.md). Phase 0 acceptance remains pending until the PostgreSQL-backed application and browser sign-in are verified.

## API

See [docs/PHASE_0_API.md](docs/PHASE_0_API.md) for working endpoint contracts and curl examples. All future financial paths require authentication; their business endpoints are not implemented. The authenticated `/auth/me` UUID is the ownership boundary for Phase 1. No request may supply its own authoritative owner ID.

## Layout and next phase

- `backend/`: Java application, authentication, Problem Details, migration and tests.
- `frontend/`: typed API client, session state, accessible forms, routes and responsive shell.
- `infrastructure/compose.yaml`: local PostgreSQL and persistent volume.
- `docs/`: product/domain contracts, milestone checklist, verification and API examples.

Remaining limitations: no password change/recovery, login rate limiting, financial features, verified backup/restore or production deployment. No bank connection, analytics or fake financial data. See [implementation plan](docs/IMPLEMENTATION_PLAN.md) for staged scope and [next prompt](docs/NEXT_PHASE_1_PROMPT.md).
