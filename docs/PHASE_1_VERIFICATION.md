# Phase 1 verification — 2026-11-03

## Outcome

Phase 0 pending acceptance was resolved before Phase 1 implementation: all original PostgreSQL/auth tests passed with Docker running. Phase 1 is now implemented and accepted with **19 backend tests and 4 browser acceptance tests passing**. No Phase 2/3 financial functionality is claimed. No existing development database or personal `.env` was queried or modified by verification.

## Versions and environment

Java toolchain 25 (installed 25.0.1); Spring Boot 4.1.2; Gradle 9.8.2 (Groovy); Node 26.13.0; Vite 7.3.7; PostgreSQL image `postgres:17`; Docker Engine 29.8.0. The official Node distribution index was fetched on 2026-11-03 and prereleases excluded: latest stable remained **v26.13.0**. No global runtime was changed. Gradle used toolchain 25; the final browser backend was explicitly started using `JAVA_HOME` pointing to JDK 25.

Docker required authorized access outside the filesystem sandbox; initial sandbox-only `docker info` returned permission denied. Authorized Docker access succeeded. There are **no remaining implementation blockers**.

## Exact verification commands/results

From the repository root, with Node 26.13.0 on PATH:

```sh
GRADLE_USER_HOME=/private/tmp/finance-gradle backend/gradlew -p backend clean test bootJar --no-daemon
```

**BUILD SUCCESSFUL in 15s**, 7 actionable tasks executed. XML reports: `OwnerBootstrapTest` 4 tests; `FixedScheduleTest` 6; `AuthenticationIntegrationTest` 9. **19 total, 0 failures, 0 skipped.** Executable JAR produced under `backend/build/libs/`.

```sh
cd frontend
npm ci --cache /private/tmp/finance-npm
npm run build
```

**npm ci succeeded**, 91 packages added. **TypeScript and Vite build passed**, 87 modules transformed, build reported 579ms. JS bundle 314.62 kB (98.58 kB gzip); CSS 10.00 kB (3.07 kB gzip). npm warned about esbuild/fsevents install scripts not covered by allowScripts; installed platform binaries worked. Dependency installation earlier reported 0 vulnerabilities; the final sandboxed clean install did not emit a new audit result, so no new audit claim is made.

```sh
PATH=/private/tmp/node-v26.13.0-darwin-arm64/bin:$PATH \
JAVA_HOME=/Users/emmanuelanokwuru/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home \
GRADLE_USER_HOME=/private/tmp/finance-gradle \
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/finance-playwright \
./infrastructure/verify-browser.sh
```

**4 passed (6.7s)**, desktop Chromium and 360×800 Chromium. Temporary PostgreSQL, backend process, environment file and credentials cleaned up by the script. Vite was started/stopped by Playwright. The script uses ports 18080/15173 and a random PostgreSQL loopback port, independently of the normal development stack.

The first browser run failed two date assertions because the display used unpadded German dates while the test expected padded day/month. The display was corrected to consistent `dd.MM.yyyy`; subsequent/final suites passed. The first frontend build command was inadvertently run from the repository root (missing script), then correctly run from `frontend/`; final build passed.

Compose was also launched using `infrastructure/compose.yaml` with `up -d --wait` in a **separate temporary project** and a temporary `ports: !override ["127.0.0.1::5432"]` override because the existing development service already occupied 5432. Container reported **Healthy** and `pg_isready -U finance_tracker -d finance_tracker` reported **accepting connections**. Only the temporary project's test container/volume were removed. The original `infrastructure-db-1` was left untouched.

`git diff --check`: passed. Git was available in this session (existing Phase 0 commit); a Phase 1 checkpoint is created after final verification/documentation.

## Behaviors covered

- Owner bootstrap normalization/hashing/config validation; PostgreSQL migrations and Hibernate schema validation.
- Health, invalid sign-in, CSRF rejection, session login/logout and HTTP-only cookie flag; unauthenticated financial request denial.
- EUR 100/3 exact cents, leap February and non-drifting Jan 31 / Feb / Mar 31 dates; large precision; rejection of zero/subcent/negative/overpayment.
- EUR 1,200/6 plan; EUR 80 leaves EUR 1,120 outstanding / EUR 120 first due remaining; another EUR 120 settles that due. Browser and HTTP integration assertions both pass.
- EUR 300 allocated EUR 200 / EUR 100 across oldest dues. Failed posts leave no payment. No ordinary transaction table/write exists.
- Identical normalized key replay returns byte-identical stored receipt; changed body or plan reuse gives 409. Replay after later payments and a void still returns the original receipt.
- Concurrent full-balance payments yield one success and one rejection; concurrent identical-key requests yield identical receipts and only one reduction.
- Foreign owner plan access/posting, missing accounts, cross-plan voids and cross-plan allocation foreign keys are rejected. The application remains single-owner; foreign-owner boundary tests use an unrelated owner UUID, not public registration of a second user.
- Invalid precision, missing request key, future dates, pre-opening payment dates, invalid pagination and sub-cent schedules are rejected with Problem Details.
- DUE/UPCOMING/PARTIALLY_PAID/OVERDUE/PAID states, progress, next unpaid date, search/pagination and void-derived balance recomputation.
- Browser sign-in/empty dashboard; Settings account setup; plan creation/detail; partial payments; void cancellation/confirmation; retained history; sign-out and subsequent 401.
- Browser exact decimal display at NUMERIC(19,2)'s upper amount and negative cents. Document width does not overflow at checked desktop/360px stages. Schedule is a labeled keyboard-focusable horizontal scroll region on narrow screens; native modal dialogs provide focus containment and Escape dismissal.

## Visual evidence

Generated screenshots under ignored `frontend/test-results/` were visually inspected at desktop and 360px. Paths:

- `installments-authenticated-adc41--void-and-responsive-layout-desktop/installment-detail.png`
- `installments-authenticated-adc41--void-and-responsive-layout-mobile-360/installment-detail.png`

They show the fixture plan after voiding EUR 80: EUR 1,080 outstanding, first due remaining EUR 80, EUR 120 paid, both history entries retained and the void labeled. These fixtures exist only in disposable test databases/screenshots, not seeded development data.

## Assumptions and limits

- Fixed total EUR plans only; schedules/account opening positions are immutable. No cancellation/write-off/edit/delete workflow, optional plan tags or aggregate summary/upcoming endpoints.
- Payments are actuals and cannot be future-dated or predate the selected account opening. Sufficient cash is not enforced; account current balances and ordinary transactions are Phase 2. Current statuses use the backend's local calendar date.
- Owner-wide locks intentionally serialize payment posting/voids across plans to safely enforce owner-scoped idempotency; plan rows are also locked. This is appropriate for the single-user MVP.
- Original payment receipts are immutable snapshots, including after voids. Read plan detail for current state. UI retry keys are retained while the detail page remains mounted; after navigation/reload, check history before resubmitting an interrupted payment. There is no financial payload persistence in browser storage.
- Authentication and money screens were tested in Chromium only. No Firefox/WebKit, full screen-reader audit, exhaustive keyboard audit, production deployment, rate limiting, password recovery, backup/restore or load test is claimed.
- Dashboard totals, transactions, recurring rules, planned events, forecasts and simulations remain planned. Phase 2/3/4 checkboxes stay unchecked.
