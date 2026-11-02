# Installment service boundary

The authenticated owner UUID is resolved by CurrentOwner; request bodies never select an owner.
InstallmentService owns plan creation, immutable due generation, payment posting and voiding.
Each payment/void transaction locks owner then plan, in that order. The owner lock protects
owner-scoped idempotency keys across plans; the plan lock prevents overpayment. Account
opening dates are immutable and constrain payment dates. No cash transfer is performed.

Payment receipt text is persisted in the same transaction as payment and allocations.
An identical key and normalized request returns that original receipt, even after later
payments or a void. Use plan detail for the current balance. Different reuse returns 409.
No deletion, schedule editing, cancellation, transactions, recurrence or forecast is exposed.
