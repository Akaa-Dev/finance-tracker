# Phase 2 — ordinary actuals, balances and explicit reconciliation

Phase 3 follow-up expanded recurring frequencies to DAILY and WEEKLY in addition to the MONTHLY/YEARLY contract described below; see `PHASE_3_API.md` for current schedule choices.

All routes use `/api/v1`, the authenticated owner's session, and CSRF for mutations. UUID ownership comes from the session; DTOs never choose an owner. Money is JSON decimal, positive for actuals/plans, at most 17 integer digits and two fractional digits. Frontend money remains decimal text until emitted as JSON numbers; it is never summed with JavaScript floats.

| Method | Route | Behavior |
|---|---|---|
| GET/POST | `/categories` | List/create INCOME or EXPENSE category; name max 120; unique owner/name/kind |
| GET/POST | `/transactions` | Paged ordinary actuals / idempotent creation (201) |
| GET | `/transactions/{id}` | Actual, supersedes ID, void reason/time and complete source-link history |
| PATCH | `/transactions/{id}` | Atomic correction: void original and create replacement (200) |
| POST | `/transactions/{id}/void` | Void with reason; retain history and reopen source |
| POST | `/transactions/{id}/link` | Reconcile one existing actual to one source without another cash movement |
| POST | `/transactions/{id}/unlink` | Retain link history; reopen source while leaving actual cash movement valid |
| GET | `/accounts/{id}/balance?asOf=YYYY-MM-DD` | Derived as-of opening/income/expense/installment breakdown and cash balance |
| GET | `/transactions/summary?from=...&to=...&accountId=...` | Period income, ordinary expenses, installment repayments, net movement and as-of cash |
| GET/POST | `/recurring-rules` | List/create fixed monthly/yearly rule |
| PATCH | `/recurring-rules/{id}` | Set `{active:true/false}` only; financial fields immutable |
| GET | `/recurring-rules/{id}/occurrences?from=...&to=...` | Read already generated occurrences and explicit realization state |
| POST | `/recurring-rules/{id}/occurrences` | Generate unique occurrence dates in `{from,to}`, max 730-day inclusive range span |
| GET/POST | `/planned-cashflows` | List/create planned one-offs; no actual posting |
| PATCH | `/planned-cashflows/{id}` | Set `{status:"PLANNED"/"CANCELLED"}` on an unrealized event |
| POST | `/planned-cashflows/{id}/realize` | Link `{transactionId}` to existing actual; no new movement |

Actual creation, correction, link/realize, recurring-rule creation and planned-event creation require a UUID `Idempotency-Key`. These Phase 2 operations share an owner/key receipt scope; Phase 1 installment receipts retain their separate scope. Reusing a key with the same operation/target/normalized body returns the original receipt text, including after later corrections/voids. Changed reuse returns 409. Void/unlink, rule active toggles, planned status changes and occurrence generation are naturally repeatable and do not require a key. Categories reject exact duplicate owner/name/kind with 409.

`GET /transactions` filters: `page=0`, `size=20` (1–100), optional `accountId`, `categoryId`, `direction=INCOME|EXPENSE`, `from`, `to`, `state=ALL|VALID|VOIDED` (default ALL). Stable ordering is transaction date descending, creation time descending, ID. Returns `{items,page,size,total}`. Summary totals use valid actuals for the selected account/date range; category/direction/state list filters do not alter the summary.

Errors use Problem Details: 400 malformed/Bean Validation/missing key; 401 authentication; 403 CSRF; 404 missing/foreign reference; 409 receipt/source/state/constraint conflict; 422 business date, precision, category, range or reconciliation rule violation. There are no transaction delete routes, automatic postings, transfers, forecasting or scenario endpoints.

## Actual and correction examples

Create categories (replace placeholders with returned UUIDs):

```http
POST /api/v1/categories
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"name":"Salary","kind":"INCOME"}
```

Create ordinary actual:

```http
POST /api/v1/transactions
Idempotency-Key: 1bda8772-4c26-4b81-ac93-330bf4060f06
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"accountId":"<account>","categoryId":"<income category>","direction":"INCOME","amount":3500.00,"transactionDate":"2026-11-01","note":"Salary","occurrenceId":null,"plannedCashflowId":null}
```

Actual dates must be on/after account opening and no later than today. The category's kind must equal direction. Future expectations belong in planned records, not actuals. No installment payment is written to this table; record repayments using the Phase 1 plan API. Transfers between one's own accounts are not supported and must not be recorded as ordinary income.

Correct an actual using a new key and full replacement body:

```http
PATCH /api/v1/transactions/<original-id>
Idempotency-Key: 49fbca89-3120-40b0-8646-f79470cfe6c2
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"reason":"Correct cents","transaction":{"accountId":"<account>","categoryId":"<income category>","direction":"INCOME","amount":3500.01,"transactionDate":"2026-11-01","note":"Corrected salary","occurrenceId":null,"plannedCashflowId":null}}
```

The original amount/date/note remain unchanged; `voidedAt`/`voidReason` are set. Replacement has `supersedesId`. If the original is linked, its link is closed with an audit reason and a new link transfers the source to the replacement in the same transaction. Replacement account/category/direction must remain compatible with the source. Do not supply new source IDs on corrections; unlink first to change the source. Failed validation rolls back the entire correction, including its link changes. Already voided/corrected originals cannot be corrected again, except identical idempotent replay.

Void or unlink:

```http
POST /api/v1/transactions/<id>/void
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"reason":"Duplicate entry"}
```

Both `/void` and `/unlink` require a nonblank reason, max 500 characters. Void removes an actual from derived cash calculations and reopens its source. Unlink leaves the cash movement valid while reopening the source. Both preserve old source-link rows with `unlinkedAt` and `unlinkReason`; neither deletes history. Repeated void does not change its original reason/time again.

## Recurrence and planned source examples

```json
{"name":"Monthly salary","accountId":"<account or null>","categoryId":"<income category>","direction":"INCOME","amount":3500.00,"frequency":"MONTHLY","anchorDate":"2026-01-31","startDate":"2026-01-01","endDate":null}
```

POST that body to `/recurring-rules` with a new key. Anchor **day** is used monthly; anchor **month/day** is used yearly. Anchor year is a template, not an extra lower date bound. Start/end determine eligibility. Jan 31 -> Feb 28 -> Mar 31; a Feb 29 yearly rule returns to Feb 29 in leap years. End is inclusive and cannot precede start. Financial rule fields are immutable; pause an old rule and create another when values change, choosing non-overlapping ranges deliberately.

Generate persisted occurrences with POST `/recurring-rules/<id>/occurrences`:

```json
{"from":"2026-01-01","to":"2026-12-31"}
```

Repeated generation returns the same IDs for the same rule/date. It posts no actuals and changes no cash balance. Paused rules reject generation; existing occurrences/links remain readable for historical reconciliation. GET reads persisted dates only and does not generate or project anything.

Create a planned one-off with a new key:

```json
{"name":"Rent","accountId":"<account or null>","categoryId":"<expense category>","direction":"EXPENSE","amount":1300.00,"expectedDate":"2026-11-02"}
```

POST to `/planned-cashflows`; starts PLANNED. No actual cash effect. Expected dates may be past or future; REALIZED cannot be set directly. A PLANNED/CANCELLED event can be cancelled/reopened. A REALIZED event must be unlinked or its actual voided before changing planned status. Financial fields remain immutable.

Reconcile an existing actual:

```http
POST /api/v1/transactions/<actual-id>/link
Idempotency-Key: 1d3b6977-f2bf-4da6-b07b-126516e53ff0
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"occurrenceId":"<occurrence-id>","plannedCashflowId":null}
```

Choose exactly one source. Alternatively POST `/planned-cashflows/<planned-id>/realize` with `{transactionId:"<actual-id>"}` and a new key, or include one source ID in the ordinary actual creation body for atomic creation + reconciliation.

The actual must be valid and match category, direction and the source account if one was specified. Actual amount/date may differ from nominal amount/expected date: the explicit link declares the **entire** source realized, not a partially fulfilled obligation. No residual is inferred. One active actual can realize only one source, and each source can have only one active actual. Owner locks serialize mutations; partial unique indexes and composite foreign keys independently enforce uniqueness and owner matching. Concurrent competing realizations yield one winner; a failed atomic create + link leaves no stray actual.

## Cash calculations and acceptance

As-of balance = eligible opening position + valid ordinary income - valid ordinary expenses - valid installment payments through that day. Before account opening it contributes zero (`openingIncluded=false`). Period net movement excludes opening positions; as-of cash includes all eligible actuals through the period's end, not just the period's start. Future as-of/summary end dates are rejected. Void state is evaluated against the **currently valid history**, so later corrections/voids restate earlier-date balances; this is not an archival snapshot query.

With opening cash €0, salary €3,500, ordinary rent €1,300 and a valid installment payment €80, summary returns income 3500.00, ordinaryExpenses 1300.00, installmentRepayments 80.00, netMovement 2120.00 and currentBalance 2120.00. Only two ordinary transaction rows exist. Recurring/planned definitions, occurrences and source links never enter cash sums. Balances may be negative and are not bank-confirmed.
