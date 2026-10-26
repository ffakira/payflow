ALTER TABLE public.account_holders
    DROP CONSTRAINT account_holders_type_check;

ALTER TABLE public.account_holders
    ADD CONSTRAINT account_holders_type_check
    CHECK (holder_type IN ('INDIVIDUAL', 'BUSINESS', 'SYSTEM'));
