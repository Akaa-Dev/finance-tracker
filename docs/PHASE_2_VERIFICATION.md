# Phase 2 verification — 2026-11-03

## Outcome and scope

Phase 2 is implemented: owner-scoped categories/ordinary actuals, append-only corrections, reasoned voids, precise as-of cash balances/period summaries, recurring rules with explicit occurrence generation, planned one-offs and auditable one-to-one realization links. **€3,500 salary − €1,300 ordinary rent − €80 installment payment = €2,120** with zero opening cash. Only two ordinary actual records are created; the installment payment remains in its separate table. No forecast/scenario engine or hypothetical posting is implemented.

Java 25, Spring Boot 4.1.2, Gradle 9.8.2 (Groovy), Node 26.13.0 and the existing frontend stack are retained. The official Node distribution index was fetched and prereleases excluded on this turn; latest stable remained v26.13.0. No global runtimes were modified.

## Baseline

Before Phase 2 edits:

- `GRADLE_USER_HOME=/private/tmp/finance-gradle backend/gradlew -p backend test --no-daemon`: **BUILD SUCCESSFUL in 14s**, all 19 existing tests passed against disposable PostgreSQL.
- From `frontend/`, with Node 26.13.0 on PATH, `npm run build`: TypeScript and Vite passed, **87 modules**, build reported **577ms**.

## Final verification

```sh
GRADLE_USER_HOME=/private/tmp/finance-gradle backend/gradlew -p backend clean test bootJar --no-daemon
```

**BUILD SUCCESSFUL in 17s**, 7 actionable tasks executed; executable JAR built. Test XML reports: `OwnerBootstrapTest` 4, `FixedScheduleTest` 6, `OccurrenceDatesTest` 5, `AuthenticationIntegrationTest` 17: **32 tests, 0 failures, 0 skipped**. A subsequent complete `test bootJar --no-daemon` run after the final planning-read snapshot/resource handling refinement also passed: **BUILD SUCCESSFUL in 16s**, all 32 tests passing.

```sh
PATH=/private/tmp/node-v26.13.0-darwin-arm64/bin:$PATH npm run build --prefix frontend
```

**TypeScript/Vite passed**, **88 modules transformed**, build reported **534ms**. Output JS **339.44 kB / 104.03 kB gzip**, CSS **12.60 kB / 3.64 kB gzip**. No dependencies were added for Phase 2; no new dependency-audit result is claimed.

```sh
PATH=/private/tmp/node-v26.13.0-darwin-arm64/bin:$PATH \
JAVA_HOME=/Users/emmanuelanokwuru/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home \
GRADLE_USER_HOME=/private/tmp/finance-gradle \
PLAYWRIGHT_BROWSERS_PATH=/private/tmp/finance-playwright \
./infrastructure/verify-browser.sh
```

**6 passed (13.1s)**: the four existing installment/auth/money-display cases and the new Phase 2 flow on both desktop Chromium and 360×800 Chromium. The script uses generated credentials, a fresh disposable PostgreSQL database and dedicated backend/frontend ports. Optional local config-file imports are disabled for this test process. Its own backend/container/environment files are cleaned up; the existing development database is not queried or changed.

`git diff --check`: passed. No ongoing implementation/toolchain blockers.

## Coverage and findings

- Existing authentication, CSRF, cookies, session lifecycle, ownership and every Phase 1 financial regression remain passing.
- Exact cents, negative opening cents, date cut-offs, pre-opening zero contribution, future/pre-opening rejection and category/direction matching.
- Separate ordinary and installment sources produce €2,120; as of the preceding day, before the payment, cash is €2,200. Voids recalculate the proper source without inserting expense duplicates.
- Concurrent identical-key actual creation returns identical receipts and one cash movement. Conflicting key reuse fails. Original receipts remain unchanged after voids.
- Monthly short-month and yearly leap-year anchors, inclusive start/end eligibility, anchor year template semantics, bounded ranges and deterministic occurrence IDs on repeated generation. Generated/paused rules do not post actuals.
- Existing-actual and atomic create-plus-link reconciliation; nominal and actual amounts may differ while the source is realized as a whole. Neither linking nor generating/planning adds another movement.
- A source cannot realize two actuals and an actual cannot realize two sources. Concurrent competing realizations have one success/one conflict; losing atomic creation leaves no stray actual.
- Corrections retain the original, append a replacement and transfer a compatible link atomically. Failed incompatible corrections roll back the original void, link changes and replacement. Voids/unlinks retain reasoned history and reopen sources.
- Database-level partial unique indexes reject multiple active sources per actual/multiple actuals per source; composite foreign keys reject foreign-owner source combinations independently of service checks.
- Pagination/date/account/category/direction/state filtering, validation, missing keys, Problem Details, as-of balance and period-summary HTTP routes.
- Browser creation of account/categories/rule/occurrence/planned event; explicitly reconciled salary/rent; €80 installment payment; immediate €2,120 summary; cents correction to €2,120.01; void reopening the source; reconciling a replacement existing actual without another cash movement. History remains visible.

Early browser runs found ambiguous text/label selectors and a stale cash-query cache after installment posting. Selectors were corrected, planning forms remount when switching their type, and installment posting/void plus account creation now invalidate cash-summary/balance queries. Final browser tests pass without waiting for stale-time expiry. Initial failed browser runs are not reported as successful.

## Visual QA

Desktop and 360px screenshots were generated under ignored `frontend/test-results/` and visually inspected. The browser tests check no horizontal document overflow at the exercised stages. Final screenshot paths:

- `transactions-actuals-recur-671a7--with-auditable-corrections-desktop/phase2-transactions.png`
- `transactions-actuals-recur-671a7--with-auditable-corrections-mobile-360/phase2-transactions.png`

They show €3,500 income, €1,300 ordinary expenses, €80 separate repayments, €2,120 net/current cash and retained void/correction/source-link history. All data is disposable test fixture data, never seeded application data.

## Assumptions, limitations and preserved work

- As-of queries use currently valid records, so later corrections/voids restate prior-date balances; this is not an archival transaction-version query. Opening positions and all eligible dated actuals, including separate installment payments, determine cash. Planned/recurring/link tables do not enter cash sums.
- Cash/planning multi-query reads use REPEATABLE READ snapshots. Writes serialize under the existing owner lock and use READ COMMITTED/default write isolation, consistent with Phase 1 owner-then-plan ordering.
- Actual source amounts/dates may differ from nominal expectations. A link realizes the entire source, not a partial financial obligation. Category/direction and any specified source account must match.
- Rule/planned financial fields are immutable; rule PATCH toggles active, planned PATCH cancels/reopens unrealized events. Pausing does not remove historical occurrences. Generation is explicit, max 730-day span; no background job or forecast is provided.
- Sources can be unlinked without voiding their cash actual; this intentionally reopens the expectation. Reasoned histories are retained. New forms retain retry keys only while mounted; after reload/navigation inspect history before resubmitting an interrupted mutation.
- Category creation has database duplicate protection rather than a posting receipt. No transfer API, ordinary-record hard delete, category editing, bank sync, automatic posting, current-account editing or sufficient-funds constraint is added.
- Planned realization chooser shows up to 100 recent valid actuals. Older records can be selected via Transactions date filters and Link source; occurrence source-picker date range is adjustable. Rule/planned lists are not paginated; ordinary actuals are.
- Browser verification is Chromium only. No full screen-reader/exhaustive keyboard audit, Firefox/WebKit, backup/restore, deployment, rate-limit or load-test claim is made. Forecasts/scenarios and remaining hardening stay unchecked.
- Pre-existing `backend/src/main/resources/application.yml` and `infrastructure/compose.yaml` edits were preserved. A separately appearing formatting-only change to `backend/src/main/java/com/financetracker/installment/api/InstallmentController.java` was also preserved. These three files are excluded from the Phase 2 checkpoint. The worktree is expected to retain those local edits afterward. README explains how to point Vite at a custom local backend port (the local config selects 8081).
