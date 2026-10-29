ALTER TABLE app_users
    ADD COLUMN failed_pin_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (failed_pin_attempts >= 0),
    ADD COLUMN pin_locked_until TIMESTAMPTZ;