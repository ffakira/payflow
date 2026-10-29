-- name: insert
INSERT INTO accounts (
    id,
    holder_id,
    account_type,
    currency_code,
    status,
    created_at
) VALUES (?, ?, ?, ?, ?, ?);

-- name: findById
SELECT
    id,
    holder_id,
    account_type,
    currency_code,
    status,
    created_at
FROM accounts
WHERE id = ?;

-- name: findByIdForUpdate
SELECT
    id,
    holder_id,
    account_number,
    account_type,
    currency_code,
    status,
    created_at
FROM accounts
WHERE id = ?
FOR UPDATE;

-- name: findCustomerAccounts
SELECT
    id,
    holder_id,
    account_number,
    account_type,
    currency_code,
    status,
    created_at
FROM accounts
WHERE account_type = 'CUSTOMER'
ORDER BY created_at DESC, id DESC
LIMIT ?;

-- name: findCustomerAccountsAfter
SELECT
    id,
    holder_id,
    account_number,
    account_type,
    currency_code,
    status,
    created_at
FROM accounts
WHERE account_type = 'CUSTOMER'
    AND (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT ?;

-- name: existsByHolderId
SELECT EXISTS (
    SELECT 1
    FROM accounts
    WHERE holder_id = ?
        AND account_type = 'CUSTOMER'
);
