CREATE TABLE IF NOT EXISTS momna.config_definitions (
    config_key VARCHAR(160) PRIMARY KEY,
    value_type VARCHAR(32) NOT NULL,
    owner VARCHAR(160) NOT NULL,
    required BOOLEAN NOT NULL,
    bootstrap_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS momna.config_versions (
    config_key VARCHAR(160) NOT NULL REFERENCES momna.config_definitions(config_key),
    version INTEGER NOT NULL CHECK (version > 0),
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    environment VARCHAR(16) NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    value_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PUBLISHED','RETIRED')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (config_key, environment, version),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE INDEX IF NOT EXISTS idx_config_versions_effective ON momna.config_versions(config_key, environment, status, valid_from, version DESC);

CREATE TABLE IF NOT EXISTS momna.feature_flag_definitions (
    flag_key VARCHAR(160) PRIMARY KEY,
    owner VARCHAR(160) NOT NULL,
    module_id VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_feature_flag_module_kill_switch ON momna.feature_flag_definitions(module_id) WHERE module_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS momna.feature_flag_segments (
    segment_id VARCHAR(160) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    environment VARCHAR(16) NOT NULL,
    countries JSONB NOT NULL DEFAULT '[]'::jsonb,
    locales JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PUBLISHED','RETIRED')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(segment_id, version, environment),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE TABLE IF NOT EXISTS momna.feature_flag_versions (
    flag_key VARCHAR(160) NOT NULL REFERENCES momna.feature_flag_definitions(flag_key),
    version INTEGER NOT NULL CHECK (version > 0),
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    environment VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PUBLISHED','RETIRED')),
    default_enabled BOOLEAN NOT NULL,
    rollout_algorithm_version VARCHAR(80) NOT NULL,
    rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(flag_key, environment, version),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE INDEX IF NOT EXISTS idx_feature_flag_versions_effective ON momna.feature_flag_versions(flag_key, environment, status, valid_from, version DESC);

CREATE TABLE IF NOT EXISTS momna.config_flag_operations (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    entity_type VARCHAR(16) NOT NULL CHECK (entity_type IN ('CONFIG','FLAG')),
    entity_key VARCHAR(160) NOT NULL,
    entity_version INTEGER NOT NULL,
    environment VARCHAR(16) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
