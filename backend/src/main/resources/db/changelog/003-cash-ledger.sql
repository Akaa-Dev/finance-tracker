CREATE TABLE categories (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), name VARCHAR(120) NOT NULL,
 kind VARCHAR(7) NOT NULL CHECK(kind IN ('INCOME','EXPENSE')), created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,user_id), UNIQUE(user_id,name,kind)
);
CREATE TABLE transactions (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), account_id UUID NOT NULL, category_id UUID NOT NULL,
 direction VARCHAR(7) NOT NULL CHECK(direction IN ('INCOME','EXPENSE')), amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
 transaction_date DATE NOT NULL, note VARCHAR(2000), created_at TIMESTAMPTZ NOT NULL,
 voided_at TIMESTAMPTZ, void_reason VARCHAR(500), supersedes_id UUID UNIQUE,
 FOREIGN KEY(account_id,user_id) REFERENCES cash_accounts(id,user_id),
 FOREIGN KEY(category_id,user_id) REFERENCES categories(id,user_id), UNIQUE(id,user_id),
 FOREIGN KEY(supersedes_id,user_id) REFERENCES transactions(id,user_id),
 CHECK ((voided_at IS NULL AND void_reason IS NULL) OR (voided_at IS NOT NULL AND length(trim(void_reason))>0))
);
CREATE INDEX transactions_owner_date ON transactions(user_id,transaction_date,id);
CREATE INDEX transactions_account_date ON transactions(account_id,transaction_date);
CREATE TABLE recurring_rules (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES app_users(id),account_id UUID,category_id UUID NOT NULL,
 name VARCHAR(120) NOT NULL,direction VARCHAR(7) NOT NULL CHECK(direction IN ('INCOME','EXPENSE')),
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0),frequency VARCHAR(7) NOT NULL CHECK(frequency IN ('MONTHLY','YEARLY')),
 anchor_date DATE NOT NULL,start_date DATE NOT NULL,end_date DATE,active BOOLEAN NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,UNIQUE(id,user_id),
 FOREIGN KEY(account_id,user_id) REFERENCES cash_accounts(id,user_id),FOREIGN KEY(category_id,user_id) REFERENCES categories(id,user_id),
 CHECK(end_date IS NULL OR end_date>=start_date)
);
CREATE INDEX rules_owner ON recurring_rules(user_id);
CREATE TABLE recurrence_occurrences (
 id UUID PRIMARY KEY,user_id UUID NOT NULL,rule_id UUID NOT NULL,occurrence_date DATE NOT NULL,
 planned_amount NUMERIC(19,2) NOT NULL CHECK(planned_amount>0),UNIQUE(id,user_id),UNIQUE(rule_id,occurrence_date),
 FOREIGN KEY(rule_id,user_id) REFERENCES recurring_rules(id,user_id)
);
CREATE INDEX occurrences_owner_date ON recurrence_occurrences(user_id,occurrence_date);
CREATE TABLE planned_cashflows (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES app_users(id),account_id UUID,category_id UUID NOT NULL,
 name VARCHAR(120) NOT NULL,direction VARCHAR(7) NOT NULL CHECK(direction IN ('INCOME','EXPENSE')),
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0),expected_date DATE NOT NULL,
 status VARCHAR(10) NOT NULL CHECK(status IN ('PLANNED','REALIZED','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,UNIQUE(id,user_id),
 FOREIGN KEY(account_id,user_id) REFERENCES cash_accounts(id,user_id),FOREIGN KEY(category_id,user_id) REFERENCES categories(id,user_id)
);
CREATE INDEX planned_owner_date ON planned_cashflows(user_id,expected_date);
CREATE TABLE actual_links (
 id UUID PRIMARY KEY,user_id UUID NOT NULL,transaction_id UUID NOT NULL,occurrence_id UUID,planned_cashflow_id UUID,
 linked_at TIMESTAMPTZ NOT NULL,unlinked_at TIMESTAMPTZ,unlink_reason VARCHAR(500),
 FOREIGN KEY(transaction_id,user_id) REFERENCES transactions(id,user_id),
 FOREIGN KEY(occurrence_id,user_id) REFERENCES recurrence_occurrences(id,user_id),
 FOREIGN KEY(planned_cashflow_id,user_id) REFERENCES planned_cashflows(id,user_id),
 CHECK ((occurrence_id IS NULL)<>(planned_cashflow_id IS NULL)),
 CHECK ((unlinked_at IS NULL AND unlink_reason IS NULL) OR (unlinked_at IS NOT NULL AND length(trim(unlink_reason))>0))
);
CREATE UNIQUE INDEX links_one_source_per_actual ON actual_links(transaction_id) WHERE unlinked_at IS NULL;
CREATE UNIQUE INDEX links_one_actual_per_occurrence ON actual_links(occurrence_id) WHERE unlinked_at IS NULL;
CREATE UNIQUE INDEX links_one_actual_per_planned ON actual_links(planned_cashflow_id) WHERE unlinked_at IS NULL;
CREATE TABLE operation_receipts (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES app_users(id),idempotency_key UUID NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,receipt TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL,UNIQUE(user_id,idempotency_key)
);
