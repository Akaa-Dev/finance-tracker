# Data model and financial invariants

## Relational model (logical)
Use Liquibase versioned migrations. The following is the required initial logical design; Codex should write working SQL migrations and JPA mappings.

- `app_users`: `id uuid PK`, unique normalized email, password hash, base currency (EUR), timestamps.
- `cash_accounts`: `id`, `user_id FK`, name, opening balance NUMERIC(19,2), opening date, timestamps. Do not infer opening balance from installments.
- `installment_plans`: `id`, `user_id FK`, name, provider, description, currency, principal amount, total payable, installment count, first due date, administrative status (ACTIVE/CANCELLED), timestamps.
- `installment_dues`: `id`, `plan_id FK`, sequence number, due date, amount. Unique(plan_id, sequence number). Sum of dues equals plan total payable exactly.
- `installment_payments`: `id`, `plan_id FK`, `account_id FK`, payment date, amount, note, `idempotency_key`, request fingerprint, voided timestamp, created timestamp; unique(owner, idempotency key) or appropriate scoped key.
- `payment_allocations`: `id`, `payment_id FK`, `due_id FK`, amount; unique(payment_id, due_id).
- `categories`: `id`, `user_id FK`, name, kind INCOME/EXPENSE, unique(user_id, name, kind).
- `transactions`: `id`, `user_id FK`, `account_id FK`, `category_id FK`, direction INCOME/EXPENSE, amount, transaction date, note, optional `recurrence_occurrence_id`, voided timestamp. Ordinary transactions only (NEVER duplicate installment payments).
- `recurring_rules`: `id`, `user_id FK`, optional account_id, category_id, name, direction, amount, frequency MONTHLY/YEARLY, anchor day/date, start/end, active, timestamps.
- `recurrence_occurrences` (recommended): `id`, `rule_id FK`, occurrence date, planned amount, linked actual transaction ID nullable, unique(rule_id, occurrence date). Can be materialized or virtual if exact reconciliation is implemented.
- `planned_cashflows`: `id`, `user_id FK`, optional account_id, category_id, name, direction, amount, expected date, status PLANNED/REALIZED/CANCELLED, optional actual transaction link.

All FKs and owner checks must be enforced by domain operations. Add helpful owner/date and plan/date indexes. Consider composite foreign keys or robust transactional checks where row-to-row ownership integrity matters.

## Derived values
- Plan `totalPaid = SUM(valid installment_payments.amount)`.
- Plan `outstanding = totalPayable - totalPaid`.
- Due `allocated = SUM(allocations for valid payments)`; `remaining = amount - allocated`.
- Account `currentBalance = openingBalance + valid ordinary income - valid ordinary expense - valid installment payments` as of a given date; exclude events before opening date or explicitly reject them. Avoid importing the same bank movement twice.
- Dashboard debt = sum of outstanding across non-cancelled valid plans; cancelled plan semantics must not silently delete unpaid obligations. For v1 prohibit cancelling a plan with remaining balance unless a formally defined write-off workflow is added.

## Allocation algorithm
Under `@Transactional`, lock the plan (PESSIMISTIC_WRITE) and load dues in dueDate/sequence order. Validate account ownership, plan owner, positive amount, exact currency precision, non-cancelled plan, idempotency, and amount <= outstanding. Save payment; allocate each cent to earliest due with remaining balance; assert sum allocations equals payment amount. If any invariant fails, roll back. Return fully recalculated snapshot.

## Round-off policy
For an even fixed schedule, divide total payable by installment count at scale 2 using DOWN for first N-1 dues; last due receives remainder. Example €100.00/3 => €33.33, €33.33, €33.34. Due dates: next month same anchor day, clamped to month's last day without shifting the original anchor day; e.g. Jan 31 -> Feb 28 -> Mar 31.

## Key edge cases and tests
- €100.00/3 scheduled with exact sum.
- €200 due, pay €80 then €120: remaining €120 then zero.
- Payment of €300 across a €200 due and the next €200: allocations €200/€100.
- Void an €80 payment and recompute due and overall plan balance correctly.
- Reject €0, negative, 3+ decimal places, overpayment, foreign user payment, foreign account, foreign due.
- Same idempotency key and payload yields same receipt; same key with different payload yields 409.
- Simultaneous requests for remaining €100 cannot both settle it.
- Overdue partially paid obligation appears immediately in forecast; payment already posted is not deducted again.
- Actual salary linked to November recurrence must not be projected again in November.
- Cash balances roll forward using exact dates, not only monthly arithmetic.

## Phase 2 physical implementation notes

Migration `003-cash-ledger` adds ordinary `transactions`, `categories`, `recurring_rules`, `recurrence_occurrences`, `planned_cashflows`, `actual_links` and `operation_receipts`. Monetary columns are NUMERIC(19,2). Composite owner foreign keys cover account/category/source references. Partial unique indexes on active links enforce one source per actual and one actual per occurrence/planned event.

Actuals are immutable apart from void metadata. A correction atomically voids the old row and appends a replacement with unique `supersedes_id`. Source realization lives in `actual_links` rather than nullable duplicated realization columns: closed link rows retain timestamps/reasons, and the active row identifies the current actual. Voids reopen planned events or leave occurrences PENDING; corrections transfer links only if category/direction/specified account remain compatible. Ordinary actuals and installment payments remain separate physical sources.

Phase 2 idempotency receipts cover actual creation/correction/linking and planned/rule creation; owner locks serialize these with existing installment writes. Receipt snapshots stay immutable after later corrections. Cash reads use a REPEATABLE READ snapshot across opening positions, valid ordinary actuals and valid installment payments. They ignore planned definitions/links entirely, and do not include transfers (no transfer API exists).

Rules retain fixed financial fields; PATCH toggles active only. Planned PATCH changes unrealized status only. Occurrence generation is explicit and bounded (max 730-day range), unique by rule/date, and never posts an actual. Anchor year is a template; monthly uses day and yearly uses month/day with last-day clamping. Active links reconcile whole nominal occurrences/events even if actual amount/date differ. Phase 3 must exclude realized sources using active links joined to valid actuals, without projecting a nominal residual. The forecast engine is not implemented in Phase 2.

## Phase 3 derived projection

No new financial tables or migration are required. A read-only snapshot loads owner-scoped accounts, valid ordinary actuals, payments, dues/allocations, recurring rules, occurrences, planned events and active links. Cash starts from the same derived as-of ledger as Phase 2. Posted payments enter cash once as payment events and reduce future due remainders through valid allocations. A linked valid actual suppresses its entire recurring or planned nominal source. Virtual future recurrence dates use the existing anchor calculation and are not persisted. Daily and monthly balances are folded from exact `BigDecimal` events. Hypothetical changes alter only the in-memory event list; persisted rows remain unchanged.

Follow-up migration `004-recurring-frequency` expands the recurring-rule frequency check to DAILY and WEEKLY. It does not alter existing monthly/yearly rows or generate actuals. Debt graph values are derived from plan totals, valid payments, and projected due remainders; no mutable debt balance column is added. Account cards use current derived actual balances. An account-free expected source contributes to the combined forecast, without assigning that expectation to a specific account.
