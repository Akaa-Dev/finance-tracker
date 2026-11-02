CREATE TABLE cash_accounts (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), name VARCHAR(120) NOT NULL,
 opening_balance NUMERIC(19,2) NOT NULL, opening_date DATE NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(id,user_id)
);
CREATE INDEX accounts_owner ON cash_accounts(user_id);
CREATE TABLE installment_plans (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), name VARCHAR(120) NOT NULL,
 provider VARCHAR(120) NOT NULL, description VARCHAR(2000), currency VARCHAR(3) NOT NULL CHECK(currency='EUR'),
 principal_amount NUMERIC(19,2) CHECK(principal_amount>=0), total_payable NUMERIC(19,2) NOT NULL CHECK(total_payable>0),
 installment_count INTEGER NOT NULL CHECK(installment_count BETWEEN 1 AND 600), first_due_date DATE NOT NULL,
 administrative_status VARCHAR(16) NOT NULL CHECK(administrative_status IN ('ACTIVE','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(id,user_id), CHECK(principal_amount IS NULL OR principal_amount<=total_payable)
);
CREATE INDEX plans_owner_date ON installment_plans(user_id,first_due_date,id);
CREATE TABLE installment_dues (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, plan_id UUID NOT NULL, sequence_number INTEGER NOT NULL CHECK(sequence_number>0),
 due_date DATE NOT NULL, amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
 FOREIGN KEY(plan_id,user_id) REFERENCES installment_plans(id,user_id),
 UNIQUE(plan_id,sequence_number), UNIQUE(id,plan_id,user_id)
);
CREATE INDEX dues_plan_date ON installment_dues(plan_id,due_date,sequence_number);
CREATE TABLE installment_payments (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, plan_id UUID NOT NULL, account_id UUID NOT NULL,
 paid_on DATE NOT NULL, amount NUMERIC(19,2) NOT NULL CHECK(amount>0), note VARCHAR(2000),
 idempotency_key UUID NOT NULL, request_fingerprint VARCHAR(64) NOT NULL, receipt TEXT NOT NULL,
 voided_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(plan_id,user_id) REFERENCES installment_plans(id,user_id),
 FOREIGN KEY(account_id,user_id) REFERENCES cash_accounts(id,user_id),
 UNIQUE(user_id,idempotency_key), UNIQUE(id,plan_id,user_id)
);
CREATE INDEX payments_plan_date ON installment_payments(plan_id,paid_on);
CREATE INDEX payments_owner_account_date ON installment_payments(user_id,account_id,paid_on);
CREATE TABLE payment_allocations (
 id UUID PRIMARY KEY, user_id UUID NOT NULL, plan_id UUID NOT NULL, payment_id UUID NOT NULL, due_id UUID NOT NULL,
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
 FOREIGN KEY(payment_id,plan_id,user_id) REFERENCES installment_payments(id,plan_id,user_id),
 FOREIGN KEY(due_id,plan_id,user_id) REFERENCES installment_dues(id,plan_id,user_id), UNIQUE(payment_id,due_id)
);
CREATE INDEX allocations_due ON payment_allocations(due_id);
