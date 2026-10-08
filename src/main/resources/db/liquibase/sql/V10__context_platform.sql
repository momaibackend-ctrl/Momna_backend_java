CREATE TABLE IF NOT EXISTS momna.context_purpose_policies (
    purpose_key TEXT NOT NULL,
    purpose_version INTEGER NOT NULL CHECK (purpose_version > 0),
    policy_schema_version INTEGER NOT NULL CHECK (policy_schema_version > 0),
    context_schema_version INTEGER NOT NULL CHECK (context_schema_version > 0),
    output_schema_version INTEGER NOT NULL CHECK (output_schema_version > 0),
    policy_json JSONB NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (purpose_key, purpose_version)
);

CREATE TABLE IF NOT EXISTS momna.context_snapshots (
    snapshot_id UUID PRIMARY KEY,
    user_id TEXT NOT NULL,
    purpose_key TEXT NOT NULL,
    purpose_version INTEGER NOT NULL,
    policy_schema_version INTEGER NOT NULL CHECK (policy_schema_version > 0),
    context_schema_version INTEGER NOT NULL CHECK (context_schema_version > 0),
    output_schema_version INTEGER NOT NULL CHECK (output_schema_version > 0),
    operation_id TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL CHECK (length(request_fingerprint) = 64),
    content_fingerprint TEXT NOT NULL CHECK (length(content_fingerprint) = 64),
    reference_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    snapshot_version BIGINT NOT NULL DEFAULT 1 CHECK (snapshot_version = 1),
    snapshot_json JSONB NOT NULL,
    FOREIGN KEY (purpose_key, purpose_version)
        REFERENCES momna.context_purpose_policies(purpose_key, purpose_version),
    UNIQUE (user_id, purpose_key, purpose_version, operation_id)
);

CREATE INDEX IF NOT EXISTS idx_context_snapshots_subject_purpose
    ON momna.context_snapshots(user_id, purpose_key, purpose_version, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_context_snapshots_fingerprint
    ON momna.context_snapshots(user_id, purpose_key, content_fingerprint);

CREATE OR REPLACE FUNCTION momna.reject_context_snapshot_update()
RETURNS trigger
LANGUAGE plpgsql
AS 'BEGIN
    RAISE EXCEPTION ''context snapshots are immutable'';
END;';

DROP TRIGGER IF EXISTS trg_context_snapshot_immutable ON momna.context_snapshots;
CREATE TRIGGER trg_context_snapshot_immutable
BEFORE UPDATE ON momna.context_snapshots
FOR EACH ROW EXECUTE FUNCTION momna.reject_context_snapshot_update();

INSERT INTO momna.context_purpose_policies(
    purpose_key, purpose_version, policy_schema_version, context_schema_version, output_schema_version, policy_json
)
VALUES
(
    'PROFILE_CONTEXT',
    1,
    1,
    1,
    1,
    '{
      "allowedConsumers":["core.flows","modules.myday"],
      "fieldResolutionPurpose":"PROFILE_CONTEXT",
      "allowedSensitivityScopes":["GENERAL_PROFILE","HER_PRIVATE","MEDICAL_PRIVATE"],
      "requiresConsent":true,
      "consentScope":"GENERAL_PROFILE",
      "snapshotMode":"OPTIONAL",
      "sources":[
        {"kind":"CANONICAL_FIELD","sourceKey":"profile.birth_date","required":false},
        {"kind":"CANONICAL_FIELD","sourceKey":"calendar.period_start_date","required":false}
      ]
    }'::jsonb
),
(
    'AGENT_CONTEXT',
    1,
    1,
    1,
    1,
    '{
      "allowedConsumers":["platform.ai"],
      "fieldResolutionPurpose":"AGENT_CONTEXT",
      "allowedSensitivityScopes":["GENERAL_PROFILE","HER_PRIVATE","MEDICAL_PRIVATE"],
      "requiresConsent":true,
      "consentScope":"GENERAL_PROFILE",
      "snapshotMode":"REQUIRED",
      "sources":[
        {"kind":"CANONICAL_FIELD","sourceKey":"lifecycle.pregnancy_status","required":false},
        {"kind":"CANONICAL_FIELD","sourceKey":"checkin.symptom_bloating","required":false}
      ]
    }'::jsonb
)
ON CONFLICT (purpose_key, purpose_version) DO NOTHING;

COMMENT ON TABLE momna.context_purpose_policies IS
    'Versioned allowlisted Context Platform purpose policies; no universal full-profile purpose exists.';
COMMENT ON TABLE momna.context_snapshots IS
    'Immutable reproducible Context Platform snapshots. Redis may cache references only; PostgreSQL is durable source of truth.';
