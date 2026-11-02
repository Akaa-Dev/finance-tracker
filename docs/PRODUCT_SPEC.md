# Product specification — Finance Tracker MVP

## Product goal
Provide a single, private place to record installment commitments and payments, understand total remaining debt, track income and normal expenses, and forecast whether upcoming commitments can be covered. Responsive for laptop and phone.

## Personas and assumptions
- Personal owner; authenticated single-user MVP. Owner-scoped tables from the beginning for safe future expansion.
- Default currency EUR, German number/date formatting in UI (configurable later), English interface initially.
- Manually entered data. No bank connectivity in MVP, no automatic money transfer, no credit advice.
- Monetary balances are factual only when starting cash balances and entered transactions are complete.

## Navigation
Dashboard | Installments | Transactions | Forecast | Settings.

## Installment plan
Fields: name, provider/merchant, optional description, total contractual payable, optional original purchase/principal amount, total number of scheduled monthly installments, first due date, currency (EUR), optional tags. v1 automatically generates fixed monthly due dates with a defined end-of-month policy and last-cent rounding. Once payments exist, don't silently regenerate the schedule.

Plan list and detail must show: total payable, paid amount, outstanding amount, percentage paid, next unpaid due, overdue amount, projected final due date, schedule and full payment history. The displayed derived status may be active, paid, overdue; an administrative cancellation is a separate stored state.

## Record payment
Select plan and cash account; amount, payment date, optional note; submit with an idempotency key. Allocate oldest unpaid due first. Allow partial, full, early, and multi-due payments. Return allocations, revised outstanding plan amount and amounts remaining per due. A due is `UPCOMING`, `DUE`, `PARTIALLY_PAID`, `OVERDUE`, or `PAID` derived from due date plus allocated amounts (partial overdue should show overdue as primary state and partial progress separately). Never overpay; preserve cents.

## Income, expenses, and cash accounts
Track manual cash accounts with an opening balance and date. Track actual income/expense transactions including amount, category, date, account, note. Maintain recurring rules for salary, rent, utilities, subscriptions, etc., with frequency, anchor day/date, start/end and optional linked account. Track one-off planned future events separately. Distinguish actual payments from planned events; support linking a recurring occurrence to an actual transaction (or documented deterministic reconciliation) to avoid duplicates. All records owner-scoped.

## Dashboard
- Current total cash balance from opening balances + eligible actual income - eligible actual expenses - valid installment payments.
- Total contractual outstanding debt across plans.
- Upcoming installments in 7/30 days and overdue amounts.
- Current month income, ordinary expenses, debt repayments, and net cash movement separately.
- Progress per plan and recent activity.
- Show clear warnings if data is incomplete; do not present balances as bank-confirmed.

## Forecasting
Produce date-wise projection and monthly aggregates for horizon of 1–12 months: initial cash position, future recurring income and expenses, future one-off planned events, outstanding scheduled installment obligations, projected lowest balance, and monthly ending balances. Forecast from user's chosen date; for historical/in-current-month reconciliation distinguish actual booked cash movements from future projections. Overdue unpaid installments are projected as due immediately on the forecast start date. Do not double-count realized recurring occurrences and posted installment payments. v1 deterministic nominal values; no fuzzy AI forecasting.

Support an ephemeral what-if request with an additional fixed installment schedule, hypothetical one-off expense, or future income reduction; return baseline vs scenario by month and minimum daily balance. No persistence.

## Authentication and privacy
Single owner account initialized through a safe setup/bootstrap flow. Secure password hashing, HTTP-only session cookie or equivalent secure authentication, CSRF protection appropriate to mechanism, ownership checks for every record, no secrets in repo. No automatic bank syncing, no external analytics collecting financial data.

## Non-goals for MVP
Bank sync/import, shared budgets, investments, tax returns, debt prioritization algorithms, interest accrual/amortization, variable-rate loans, multiple currencies, advanced notification delivery, native mobile application.

## UX acceptance
Mobile navigation works at 360px width; no horizontal overflow; forms usable with keyboard and screen reader; all numbers use EUR display; loading, empty, success and error states; confirm reversing a payment; strong distinction between planned and actual activity.
