# Phase 3 verification — 2026-11-03

## Scope and result

Phase 3 adds an authenticated owner-scoped forecast service and HTTP routes, exact daily/monthly cash projections, explicit source events, and read-only what-if scenarios. It reuses Phase 0–2 tables; no migration is needed. The Forecast page shows monthly chart/table, collapsible daily balances, source list, and one-change scenario comparison. Existing income actual, recurring rule and planned one-off forms support user-entered income sources; the page links to those flows and shows pending future income by source name.

## Baseline before edits

- `GRADLE_USER_HOME=/private/tmp/finance-gradle ./gradlew test --no-daemon` in `backend/`: **BUILD SUCCESSFUL in 17s**, existing 32 tests passed with disposable PostgreSQL. The existing authenticated `/api/v1/transactions/summary` integration assertion returned 200.
- `PATH=/private/tmp/node-v26.13.0-darwin-arm64/bin:$PATH npm run build` in `frontend/`: passed, 88 modules transformed, Vite reported 553ms.
- The old local dev processes were not restarted or used as proof of current routes. A Java listener on 8081 predates the Phase 2 build, which explains the previously reported missing summary route despite working login. The disposable PostgreSQL/HTTP tests exercise the current compiled code without touching the local development database.

## Final verification

- `GRADLE_USER_HOME=/private/tmp/finance-gradle ./gradlew clean test bootJar --no-daemon` in `backend/`: **BUILD SUCCESSFUL in 19s**, 7 actionable tasks executed. XML reports **35 tests, 0 failures, 0 errors, 0 skipped**: 17 existing HTTP/PostgreSQL integration tests, 6 fixed-schedule tests, 5 occurrence-date tests, 4 owner-bootstrap tests, 2 forecast calculation tests and 1 new PostgreSQL/HTTP forecast integration test. Executable JAR built.
- `PATH=/private/tmp/node-v26.13.0-darwin-arm64/bin:$PATH npm run build` in `frontend/` after the final copy edit: TypeScript/Vite passed; **89 modules transformed**, reported **534ms**, JS 348.76 kB (105.97 kB gzip), CSS 13.99 kB (3.90 kB gzip).
- With the pinned Node, Java and temporary Gradle/Playwright paths, `./infrastructure/verify-browser.sh`: **6 passed (14.3s)** in desktop and 360×800 Chromium using generated credentials and a disposable PostgreSQL container. The script cleaned up its own container/processes. It did not access the normal development database.
- `git diff --check`: passed after implementation.

The forecast PostgreSQL test verifies authenticated GET and CSRF-protected POST, linked recurring income and realized planned income suppressed, a pending planned expense, partially paid overdue due, exact ending/lowest balances, future-start carry-in, hypothetical expense/new installment/income reduction, invalid precision, owner-scoped rule rejection, and unchanged plan/payment/transaction row counts after scenarios. A unit test checks cents, month boundaries, daily lows and future-start carry. Browser flow checks itemized remaining installment due, absence of realized nominal income in the current period, future recurring income shown by name, exact scenario delta, unchanged actual cash summary, and no horizontal overflow at 360px.

## Visual QA and limitations

The desktop and 360px Forecast screenshots under ignored `frontend/test-results/` were visually inspected. The mobile view uses stacked summary cards and a chart, with scrollable exact-value tables. Browser fixtures from earlier cases share the disposable test owner, so the all-account forecast intentionally includes their balances and dues; assertions scope source names and check exact deltas rather than pretending a single-account total.

Forecasts use current valid history and fixed nominal future amounts. Later voids/corrections restate earlier forecasts. An unlinked actual may overlap an expected future source; the API warns rather than applying a heuristic match. A linked source is considered fully realized even when the actual amount/date differs. The current UI compares one hypothetical change per request; the API accepts up to ten. There is no bank sync, multi-currency, variable interest, automatic transfer, persisted scenario, account filtering on forecasts, or archival forecast snapshot. No Firefox/WebKit or full screen-reader audit was performed. Phase 4 hardening remains open. Local older backend/Vite processes must be restarted to serve new routes; the task did not alter those user-managed processes.
