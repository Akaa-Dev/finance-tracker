# Phase 3 forecast API

All routes require the authenticated owner's session. POST scenarios also require the normal session CSRF token. The API is read-only with respect to financial records; a scenario needs no idempotency key because it stores nothing. Money remains JSON decimal. The frontend preserves returned money as decimal text and never uses floating-point arithmetic to calculate balances.

## Baseline

```http
GET /api/v1/forecasts/cashflow?from=2026-11-03&to=2027-04-30
```

`from` and `to` are inclusive ISO dates, ordered, at most 366 days apart, and within the 12-month window around today. The response includes `startingBalance`, `endingBalance`, `lowestDailyBalance`, `startingDebt`, `endingDebt`, `accountBalances` (each account's current actual balance), `actualThrough`, dated `events`, each calendar `day`, and `months` with income, ordinary expenses, installment repayments, opening positions, ending balance, lowest daily balance and remaining debt. Events carry `source`, `sourceId` where persisted, `name`, `amount` and `projected`. Source types are `OPENING_BALANCE`, `ACTUAL_INCOME`, `ACTUAL_EXPENSE`, `ACTUAL_PAYMENT`, `RECURRING_INCOME`, `RECURRING_EXPENSE`, `PLANNED_INCOME`, `PLANNED_EXPENSE`, `INSTALLMENT_DUE`, and hypothetical variants for scenarios. `HYPOTHETICAL_OBLIGATION` increases simulated debt without changing cash.

Starting cash is derived from account opening positions and valid actual ordinary transactions and installment payments. For a future `from`, future recurring/planned cash flows and account openings before `from` are carried into `startingBalance`. Historical days in the visible range use posted actual movements; only days after today use expectations. Future opening positions appear separately from income. All accounts are combined. No bank-confirmed balance is claimed.

Active recurring rules generate virtual scheduled dates without writing occurrence rows. Existing explicitly linked and valid actuals suppress the entire corresponding recurrence. Paused rules are excluded. Only pending unrealized planned one-offs are included. Remaining dues equal due amount minus allocations of valid payments; a partially paid or overdue remainder is projected on the later of its due date and forecast projection start. Posted payments appear only as actual cash movement and are never ordinary expense transactions. Unlinked actuals can overlap a future expectation; the warning calls this out instead of guessing a match.

Recurring frequencies are DAILY, WEEKLY, MONTHLY and YEARLY for both income and expense. DAILY repeats every eligible date; WEEKLY repeats on the anchor weekday; MONTHLY keeps the anchor day with month-end clamping; YEARLY keeps anchor month/day with leap-year clamping. ONE_OFF uses `/planned-cashflows` instead of a recurring rule. The Forecast form lets the owner choose both direction and descriptive category, plus one of these five schedule choices. All entries are expectations; they do not post actual cash.

## Read-only scenario

```http
POST /api/v1/forecasts/scenarios
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"from":"2026-11-03","to":"2027-04-30","changes":[{"type":"NEW_INSTALLMENT","amount":150.00,"firstDueDate":"2026-12-01","installmentCount":12}]}
```

The request accepts 1–10 changes. `NEW_INSTALLMENT` uses `amount` per monthly due, `firstDueDate` and `installmentCount` (1–600); only in-range dues appear. `ONE_TIME_EXPENSE` uses positive `amount` and future `date`. `INCOME_REDUCTION` uses positive `amount`, future start `date`, and an owned active income `ruleId`; the reduction applies to each in-range future occurrence and cannot exceed the rule's nominal amount. Dates must fall in the visible future range. Amounts allow at most two decimal places.

The response contains `baseline`, `hypothetical`, `monthlyDeltas` and `lowestBalanceDelta`. Both forecasts use the same database snapshot; the request creates no plan, due, payment, actual, rule or scenario row. Invalid dates, money and types return Problem Details (422); malformed JSON returns 400, unauthenticated requests 401, missing CSRF 403. An inaccessible income rule is rejected without revealing its data.

## Income source workflow

Create an INCOME category in Settings. Record salary received as an ordinary INCOME actual in Transactions. For expected future salary, choose income, its category and a daily/weekly/monthly/yearly/one-off schedule on Forecast. Link a received actual to its occurrence or planned event through the existing explicit reconciliation flow. The forecast then itemizes only pending future income and does not add the realized nominal event again. Rules and planned sources remain separate from actual cash movements. The same schedule choices are available for EXPENSE direction.
