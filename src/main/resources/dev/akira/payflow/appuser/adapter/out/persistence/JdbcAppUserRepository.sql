-- name: insert
INSERT INTO app_users (
    id,
    holder_id,
    username,
    password_hash,
    pin_hash,
    status,
    failed_login_attempts,
    locked_until,
    created_at,
    updated_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: findByUsername
SELECT
    id,
    holder_id,
    username,
    password_hash,
    pin_hash,
    status,
    failed_login_attempts,
    locked_until,
    created_at,
    updated_at
FROM app_users
WHERE username = ?;

-- name: findByHolderId
SELECT
    id,
    holder_id,
    username,
    password_hash,
    pin_hash,
    status,
    failed_login_attempts,
    locked_until,
    created_at,
    updated_at
FROM app_users
WHERE holder_id = ?;

-- name: resetFailedLoginAttempts
UPDATE app_users
SET failed_login_attempts = 0,
    status = CASE
        WHEN status = 'LOCKED'
            AND locked_until IS NOT NULL
            AND locked_until <= ?
            THEN 'ACTIVE'
            ELSE status
        END,
    locked_until = CASE
        WHEN status = 'LOCKED'
            AND locked_until IS NOT NULL
            AND locked_until <= ?
            THEN NULL
        ELSE locked_until
        END,
    updated_at = ?
WHERE username = ?
    AND status <> 'DISABLED';

-- name: recordFailedLoginAttempts
UPDATE app_users
SET failed_login_attempts = CASE
    WHEN status = 'LOCKED'
        AND locked_until IS NOT NULL
        AND locked_until <= ?
        THEN 1
        ELSE failed_login_attempts + 1
    END,
    status = CASE
        WHEN (
            CASE
            WHEN status = 'LOCKED'
                AND locked_until IS NOT NULL
                AND locked_until <= ?
                THEN 1
                ELSE failed_login_attempts + 1
            END
        ) >= ?
        THEN 'LOCKED'
        WHEN status = 'LOCKED'
            AND locked_until IS NOT NULL
            AND locked_until <= ?
        THEN 'ACTIVE'
            ELSE status
        END,
    locked_until = CASE
        WHEN (
            CASE
            WHEN status = 'LOCKED'
                AND locked_until IS NOT NULL
                AND locked_until <= ?
                THEN 1
                ELSE failed_login_attempts + 1
            END
        ) >= ?
        THEN ?
        WHEN status = 'LOCKED'
            AND locked_until IS NOT NULL
            AND locked_until <= ?
            THEN NULL
            ELSE locked_until
        END,
    updated_at = ?
WHERE username = ?
    AND status <> 'DISABLED'
    AND (
        status <> 'LOCKED'
        OR (
            locked_until IS NOT NULL
            AND locked_until <= ?
        )
    );
