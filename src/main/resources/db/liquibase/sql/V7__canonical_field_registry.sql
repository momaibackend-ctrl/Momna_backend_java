CREATE TABLE IF NOT EXISTS momna.canonical_field_definitions (
    field_id TEXT PRIMARY KEY,
    data_type TEXT NOT NULL CHECK (data_type IN ('ENUM','DATE','NUMBER','TEXT','OBJECT')),
    domain_owner TEXT NOT NULL CHECK (domain_owner IN ('IDENTITY_PROFILE','LIFECYCLE','CALENDAR','CHECKIN','MEDICAL','DIARY','ONBOARDING','FLOW_ENGINE','MYDAY','COUPLE','MYWORLD','SCANNER','CONTENT','AGENT')),
    sensitivity_class TEXT NOT NULL CHECK (sensitivity_class IN ('GENERAL_PROFILE','HER_PRIVATE','HIS_PRIVATE','COUPLE_SHARED','MEDICAL_PRIVATE','PUBLIC_CONTENT')),
    validation_schema JSONB NOT NULL,
    definition_version INTEGER NOT NULL CHECK (definition_version > 0),
    deprecated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS momna.canonical_field_values (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    field_id TEXT NOT NULL REFERENCES momna.canonical_field_definitions(field_id),
    typed_value JSONB NULL,
    reference_provider TEXT NULL,
    reference_id TEXT NULL,
    source_type TEXT NOT NULL CHECK (source_type IN ('DOMAIN','FLOW','IMPORT','USER_CONFIRMATION','SYSTEM')),
    source_id TEXT NULL,
    flow_instance_id TEXT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('PROFILE_CONTEXT','AGENT_CONTEXT','ROUTER_TO_PERIOD_PREFILL','FEATURE_RUNTIME','HISTORY_VIEW')),
    scope_type TEXT NOT NULL CHECK (scope_type IN ('GLOBAL','PURPOSE','TRANSITION','FLOW_INSTANCE')),
    scope_id TEXT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NULL,
    confidence NUMERIC(4,3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    confirmed_by_user BOOLEAN NOT NULL DEFAULT FALSE,
    record_version BIGINT NOT NULL DEFAULT 1 CHECK (record_version > 0),
    CHECK ((typed_value IS NOT NULL) <> (reference_id IS NOT NULL)),
    CHECK ((reference_id IS NULL AND reference_provider IS NULL) OR (reference_id IS NOT NULL AND reference_provider IS NOT NULL)),
    CHECK (source_id IS NOT NULL OR flow_instance_id IS NOT NULL),
    CHECK ((scope_type = 'GLOBAL' AND scope_id IS NULL) OR (scope_type <> 'GLOBAL' AND scope_id IS NOT NULL)),
    CHECK (valid_until IS NULL OR valid_until > valid_from),
    CHECK (scope_type <> 'TRANSITION' OR purpose = 'ROUTER_TO_PERIOD_PREFILL')
);

CREATE INDEX IF NOT EXISTS idx_canonical_field_values_resolver
    ON momna.canonical_field_values(user_id, field_id, purpose, scope_type, scope_id, valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_canonical_field_values_history
    ON momna.canonical_field_values(user_id, field_id, recorded_at DESC);

INSERT INTO momna.canonical_field_definitions(field_id, data_type, domain_owner, sensitivity_class, validation_schema, definition_version)
VALUES
    ('profile.birth_date', 'DATE', 'IDENTITY_PROFILE', 'MEDICAL_PRIVATE', '{"earliest":"1900-01-01"}'::jsonb, 1),
    ('lifecycle.pregnancy_status', 'ENUM', 'LIFECYCLE', 'MEDICAL_PRIVATE', '{"allowedValues":["UNKNOWN","POSSIBLE","CONFIRMED","NOT_PREGNANT"]}'::jsonb, 1),
    ('calendar.period_start_date', 'DATE', 'CALENDAR', 'HER_PRIVATE', '{}'::jsonb, 1),
    ('checkin.symptom_bloating', 'ENUM', 'CHECKIN', 'HER_PRIVATE', '{"allowedValues":["NONE","MILD","MODERATE","SEVERE"]}'::jsonb, 1)
ON CONFLICT (field_id) DO NOTHING;
