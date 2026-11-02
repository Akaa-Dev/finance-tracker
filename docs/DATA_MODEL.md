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
