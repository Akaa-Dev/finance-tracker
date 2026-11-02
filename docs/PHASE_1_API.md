# Phase 1 API

All routes below require the owner's session. Mutations also require `X-CSRF-TOKEN` from `/api/v1/auth/csrf`. See `PHASE_0_API.md` for login/cookie commands. Money is an exact JSON decimal number (up to 17 integer and 2 fractional digits), dates ISO-8601, IDs UUIDs. The frontend preserves money as decimal strings for transport/display and never performs financial arithmetic with floats.

| Method | Path (prefix `/api/v1`) | Response |
|---|---|---|
| GET / POST | `/accounts` | Owner accounts / create opening position (201) |
| GET | `/accounts/{id}` | Owner account; foreign/missing ID is 404 |
| GET / POST | `/installments` | Page / create fixed plan and schedule (201) |
| GET | `/installments/{id}` | Derived plan detail with schedule and history |
| GET | `/installments/{id}/schedule` | Due IDs, dates, amounts, allocated, remaining, status, partiallyPaid |
| GET | `/installments/{id}/payments` | Full history including voided payments and their original allocations |
| POST | `/installments/{id}/payments` | Original receipt (200); requires UUID `Idempotency-Key` |
| POST | `/installments/{id}/payments/{paymentId}/void` | Updated plan (200); repeated void does not change the balance again |

List parameters: `page=0`, `size=20` (1–100), optional case-insensitive name `search` (max 120 characters). Response `{items, page, size, total}`. Stable sort by creation time and ID. IDs and owner scope are resolved on the server; no request accepts an authoritative owner ID.

## Example sequence

These examples describe manual requests; no data is seeded. Use the cookie jar and refreshed CSRF header described in `PHASE_0_API.md` with each request.

Create cash account:

```http
POST /api/v1/accounts
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"name":"Main account","openingBalance":2000.00,"openingDate":"2026-01-01"}
```

Create plan:

```http
POST /api/v1/installments
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"name":"Laptop","provider":"Shop","description":"Fixed total","principalAmount":1200.00,"totalPayable":1200.00,"installmentCount":6,"firstDueDate":"2026-11-15","currency":"EUR"}
```

The six dues are €200 each. €100/3 instead produces €33.33, €33.33, €33.34. January 31 dates progress to February's last day, then March 31. Counts are 1–600; each due must be at least €0.01. Principal is optional, nonnegative and cannot exceed total payable. No variable interest, regeneration, cancellation or editing API is exposed.

Record partial payment (replace the account/plan UUIDs; choose a payment date on/after account opening and no later than today):

```http
POST /api/v1/installments/<plan-uuid>/payments
Idempotency-Key: 60d972c7-dce9-4e90-8e49-72c1f364689b
Content-Type: application/json
X-CSRF-TOKEN: <session token>

{"accountId":"<account-uuid>","amount":80.00,"paidOn":"2026-11-03","note":"Partial payment"}
```

Response fields: `{id, planId, amount, allocations:[{dueId, amount, dueRemaining}], totalPaid, planOutstandingBalance}`. Outstanding is €1,120; first due remaining is €120. Post another €120 using a **new UUID key** to settle the first due, leaving €1,000 outstanding. Amounts exceeding outstanding are rejected atomically. Early payments and multi-due allocations are supported; allocation order is due date then sequence.

Identical owner/key/normalized body returns byte-identical receipt text, without inserting another payment. Amount representations `80.0`/`80.00` are equivalent; null/empty notes normalize identically. Different plan/account/amount/date/note reuse returns 409. Concurrent requests serialize under owner and plan row locks; owner-scoped key uniqueness also has a database constraint. Receipts remain original snapshots after later payments/voids; read plan detail for current values. A void does not free its original idempotency key.

Void (no body needed):

```http
POST /api/v1/installments/<plan-uuid>/payments/<payment-uuid>/void
X-CSRF-TOKEN: <session token>
```

Sets `voidedAt` once. Original payment/allocations remain. Derived plan paid/outstanding and due allocations ignore voided payments. History allocation `dueRemaining` is the **current** due remainder, while the original posting receipt contains the remainder at posting time.

Plan detail includes `totalPaid`, `outstandingBalance`, `percentagePaid`, earliest `nextDueDate`, `overdueAmount`, original scheduled `projectedFinalDueDate`, administrative/derived status, schedule and history. Overdue partial dues use primary status `OVERDUE` and `partiallyPaid=true`. Settled plans have no next due date. Date-dependent statuses use the backend's local calendar date. Current cash balances are not implemented in Phase 1; account fields describe only opening positions.

## Errors and invariants

RFC 9457 `application/problem+json`: 400 malformed/invalid payloads or missing/invalid key; 401 unauthenticated; 403 CSRF; 404 missing/foreign references; 409 conflicting key reuse/database constraint conflict; 422 financial/schedule/date/pagination rule violation. A failed post leaves no payment/allocations. Composite foreign keys reject cross-owner account/plan references and cross-plan allocations. There are no client-selected due allocation IDs.

Cash account names, plan names/providers: required, max 120 characters. Notes/descriptions: max 2000. Opening balances may be negative; payments must be strictly positive. Future payments and dates before account opening are rejected. There is no ordinary expense table or duplicate transaction write. No edit/delete routes, account current-balance endpoint, summary/upcoming aggregates, forecast, recurring or simulation endpoints are implemented here.
