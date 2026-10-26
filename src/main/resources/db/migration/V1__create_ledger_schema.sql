CREATE TABLE currencies (
    code CHAR(3) PRIMARY KEY,
    minor_unit SMALLINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT currencies_code_check
        CHECK (code ~ '^[A-Z]{3}$'),

    CONSTRAINT currencies_minor_unit_check
        CHECK (minor_unit BETWEEN 0 AND 4)
);

INSERT INTO currencies (code, minor_unit)
VALUES
    ('BRL', 2),
    ('USD', 2);

CREATE TABLE account_holders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holder_type VARCHAR(20) NOT NULL,
    display_name VARCHAR(15) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT account_holders_type_check
        CHECK (holder_type IN ('INDIVIDUAL', 'BUSINESS')),

    CONSTRAINT account_holders_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    CONSTRAINT account_holders_display_name_check
        CHECK (length(trim(display_name)) > 0)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holder_id UUID NOT NULL REFERENCES account_holders(id),
    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,
    currency_code CHAR(3) NOT NULL REFERENCES currencies(code),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT accounts_type_check
        CHECK (account_type IN (
            'CUSTOMER',
            'CLEARING',
            'FEE',
            'SUSPENSE'
        )),

    CONSTRAINT accounts_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE UNIQUE INDEX uq_customer_account_holder_currency
    ON accounts (holder_id, currency_code)
    WHERE account_type = 'CUSTOMER';

CREATE INDEX idx_accounts_holder
    ON accounts (holder_id);

CREATE TABLE exchange_rate_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    base_currency CHAR(3) NOT NULL REFERENCES currencies(code),
    quote_currency CHAR(3) NOT NULL REFERENCES currencies(code),
    rate NUMERIC(24, 12) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT exchange_rate_currencies_differ CHECK (base_currency <> quote_currency),
    CONSTRAINT exchange_rate_positive CHECK (rate > 0),
    CONSTRAINT exchange_rate_timestamps_check CHECK (fetched_at >= effective_at)
);

CREATE INDEX idx_exchange_rates_pair_effective
    ON exchange_rate_snapshots (
        base_currency,
        quote_currency,
        effective_at DESC
    );

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_type VARCHAR(30) NOT NULL,
    reference VARCHAR(100),
    fx_rate_snapshot_id UUID REFERENCES exchange_rate_snapshots(id),
    reverses_transaction_id UUID REFERENCES ledger_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ledger_transactions_type_check
        CHECK (transaction_type IN (
            'TRANSFER',
            'DEPOSIT',
            'WITHDRAWAL',
            'FX_CONVERSION',
            'FEE',
            'ADJUSTMENT',
            'REVERSAL'
        )),

    CONSTRAINT ledger_transactions_fx_check
        CHECK (
            transaction_type <> 'FX_CONVERSION'
            OR fx_rate_snapshot_id IS NOT NULL
        ),

    CONSTRAINT ledger_transactions_reversal_check
        CHECK (
            (transaction_type = 'REVERSAL')
            = (reverses_transaction_id IS NOT NULL)
        ),

    CONSTRAINT ledger_transactions_no_self_reversal
        CHECK (id <> reverses_transaction_id)
);

CREATE UNIQUE INDEX uq_ledger_transaction_reversal
    ON ledger_transactions (reverses_transaction_id)
    WHERE reverses_transaction_id IS NOT NULL;

CREATE INDEX idx_ledger_transactions_reference
    ON ledger_transactions (reference);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(6) NOT NULL,
    amount_minor BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ledger_entries_type_check
        CHECK (entry_type IN ('DEBIT', 'CREDIT')),

    CONSTRAINT ledger_entries_amount_check
        CHECK (amount_minor > 0)
);

CREATE INDEX idx_ledger_entries_account_created
    ON ledger_entries (account_id, created_at DESC, id DESC);

CREATE INDEX idx_ledger_entries_transaction
    ON ledger_entries (transaction_id);

CREATE TABLE idempotency_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holder_id UUID NOT NULL REFERENCES account_holders(id),
    operation_type VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT idempotency_requests_hash_check
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT idempotency_requests_unique
        UNIQUE (holder_id, operation_type, idempotency_key),

    CONSTRAINT idempotency_requests_transaction_unique
        UNIQUE (transaction_id)
);

CREATE FUNCTION enforce_ledger_transaction_balance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_transaction_id UUID;
    entry_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'ledger_transactions' THEN
        target_transaction_id := NEW.id;
    ELSE
        target_transaction_id := NEW.transaction_id;
    END IF;

    SELECT COUNT(*)
    INTO entry_count
    FROM ledger_entries
    WHERE transaction_id = target_transaction_id;

    IF entry_count < 2 THEN
        RAISE EXCEPTION
            'Ledger transaction % must contain at least two entries',
            target_transaction_id;
    END IF;

    IF EXISTS (
        SELECT a.currency_code
        FROM ledger_entries e
        JOIN accounts a ON a.id = e.account_id
        WHERE e.transaction_id = target_transaction_id
        GROUP BY a.currency_code
        HAVING COUNT(*) < 2
            OR SUM(
                CASE e.entry_type
                    WHEN 'DEBIT' THEN e.amount_minor
                    ELSE -e.amount_minor
                END
            ) <> 0
    ) THEN
        RAISE EXCEPTION
            'Ledger transaction % is unbalanced per currency',
            target_transaction_id;
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_transaction_balance_check
AFTER INSERT ON ledger_transactions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_ledger_transaction_balance();

CREATE CONSTRAINT TRIGGER ledger_entry_balance_check
AFTER INSERT ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_ledger_transaction_balance();

CREATE FUNCTION reject_ledger_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Ledger records are immutable; create a reversal instead';
END;
$$;

CREATE TRIGGER ledger_transactions_immutable
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER ledger_entries_immutable
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_mutation();

CREATE FUNCTION prevent_account_currency_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.currency_code IS DISTINCT FROM OLD.currency_code THEN
        RAISE EXCEPTION
            'An account currency cannot be changed';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER accounts_currency_immutable
BEFORE UPDATE OF currency_code ON accounts
FOR EACH ROW
EXECUTE FUNCTION prevent_account_currency_change();
