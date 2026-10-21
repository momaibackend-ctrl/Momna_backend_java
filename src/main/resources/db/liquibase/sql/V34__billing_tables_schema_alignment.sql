-- V29 created billing tables without schema qualification, which placed them
-- in public while the JPA entities map them to momna. Move only tables that
-- exist in public and do not already exist in momna.
DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'billing_products',
        'billing_entitlement_policies',
        'billing_provider_chain_owners',
        'billing_purchase_transactions',
        'billing_subscriptions',
        'billing_entitlements',
        'billing_provider_events'
    ]
    LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL
           AND to_regclass(format('momna.%I', table_name)) IS NULL THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA momna', table_name);
        END IF;
    END LOOP;
END
$$;
