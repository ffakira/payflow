CREATE INDEX idx_customer_accounts_created_id
    ON public.accounts (created_at DESC, id DESC)
    WHERE account_type = 'CUSTOMER';
