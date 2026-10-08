-- E17 uses the existing CORE-BE-09 tables; no onboarding-owned persistence is introduced.
-- These indexes support resumable flow lookup and idempotency/result recovery at production cardinality.
CREATE INDEX IF NOT EXISTS idx_flow_instances_definition_user_updated
    ON momna.flow_instances(definition_key, user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_flow_operation_results_created
    ON momna.flow_operation_results(instance_id, created_at DESC);
COMMENT ON TABLE momna.flow_definitions IS 'Versioned definitions for all Universal Flow Engine consumers, including onboarding; no product-specific flow tables.';
