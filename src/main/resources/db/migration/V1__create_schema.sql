CREATE TABLE wallet (
    id UUID PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wallet_non_negative_balance CHECK (balance >= 0)
);

CREATE TABLE funding_request (
    id UUID PRIMARY KEY,
    source_wallet_id UUID NOT NULL REFERENCES wallet(id),
    target_wallet_id UUID NOT NULL REFERENCES wallet(id),
    status VARCHAR(32) NOT NULL,
    total_items INTEGER NOT NULL,
    completed_items INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_funding_distinct_wallets CHECK (source_wallet_id <> target_wallet_id),
    CONSTRAINT ck_funding_status CHECK (status IN ('APPROVED', 'SUSPENDED', 'PARTIALLY_PAID', 'FULLY_PAID')),
    CONSTRAINT ck_funding_progress CHECK (
        total_items > 0 AND completed_items BETWEEN 0 AND total_items
    )
);

CREATE INDEX ix_funding_source_wallet ON funding_request(source_wallet_id);
CREATE INDEX ix_funding_target_wallet ON funding_request(target_wallet_id);

CREATE TABLE funding_schedule_item (
    id UUID PRIMARY KEY,
    funding_request_id UUID NOT NULL REFERENCES funding_request(id),
    due_date DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claim_token UUID,
    claimed_at TIMESTAMPTZ,
    CONSTRAINT ck_schedule_positive_amount CHECK (amount > 0)
);

CREATE INDEX ix_due_schedule_item ON funding_schedule_item(due_date, id);
CREATE INDEX ix_schedule_request_page
    ON funding_schedule_item(funding_request_id, due_date, id);
CREATE INDEX ix_schedule_claimable
    ON funding_schedule_item(due_date, claimed_at)
    WHERE claimed_at IS NULL;

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    source_wallet_id UUID REFERENCES wallet(id),
    target_wallet_id UUID REFERENCES wallet(id),
    schedule_item_id UUID REFERENCES funding_schedule_item(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_schedule_item UNIQUE (schedule_item_id),
    CONSTRAINT ck_payment_positive_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('CREATED', 'COMPLETED')),
    CONSTRAINT ck_payment_shape CHECK (
        (type = 'DEPOSIT' AND source_wallet_id IS NULL AND target_wallet_id IS NOT NULL) OR
        (type = 'WITHDRAWAL' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL) OR
        (type = 'TRANSFER' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL
            AND source_wallet_id <> target_wallet_id)
    ),
    CONSTRAINT ck_payment_completion_time CHECK (
        (status = 'CREATED' AND completed_at IS NULL) OR
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX ix_payment_source_wallet ON payment(source_wallet_id);
CREATE INDEX ix_payment_target_wallet ON payment(target_wallet_id);

CREATE TABLE wallet_entry (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    wallet_id UUID NOT NULL REFERENCES wallet(id),
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_entry_positive_amount CHECK (amount > 0),
    CONSTRAINT uq_entry_payment_wallet_direction
        UNIQUE (payment_id, wallet_id, direction)
);

CREATE INDEX ix_wallet_entry_wallet_time
    ON wallet_entry(wallet_id, created_at DESC);

CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(512) NOT NULL,
    payment_id UUID UNIQUE REFERENCES payment(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_idempotency_created_at
    ON idempotency_record(created_at);
