CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(trim(email))),
    password_hash VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL DEFAULT 'EUR' CHECK (base_currency = 'EUR'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    singleton BOOLEAN NOT NULL DEFAULT TRUE UNIQUE CHECK (singleton)
);
