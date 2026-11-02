# Phase 1 boundary
Resolve the owner UUID from the authenticated session, never a request-supplied owner.
Add account, plan, due, payment and allocation migrations in Phase 1. No financial
endpoints or services are implemented in Phase 0. Money uses BigDecimal and
NUMERIC(19,2); dates use LocalDate; timestamps use Instant. See docs/DATA_MODEL.md.
