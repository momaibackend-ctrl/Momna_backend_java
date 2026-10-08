CREATE TABLE IF NOT EXISTS momna.audit_events (
    event_id UUID PRIMARY KEY,
    event_type TEXT NOT NULL CHECK (
        event_type IN (
            'LIFECYCLE_TRANSITION',
            'CONSENT_CHANGE',
            'MEDICAL_CONFIRM',
            'MEDICAL_EDIT',
            'COUPLE_SHARE',
            'COUPLE_UNLINK',
            'SAFETY_DECISION',
            'DATA_EXPORT',
            'DATA_DELETE',
            'AI_GENERATION_DECISION',
            'LEGACY_TECHNICAL'
        )
    ),
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    request_id TEXT NOT NULL,
    trace_id TEXT NOT NULL,
    actor_ref TEXT NOT NULL,
    subject_ref TEXT NOT NULL,
    source_module TEXT NOT NULL,
    flow_instance_id TEXT,
    context_snapshot_id TEXT,
    action_code TEXT NOT NULL,
    result_code TEXT NOT NULL,
    policy_version TEXT,
    error_code TEXT,
    error_category TEXT,
    error_retryable BOOLEAN,
    versions_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS audit_events_trace_idx
    ON momna.audit_events (trace_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS audit_events_subject_time_idx
    ON momna.audit_events (subject_ref, occurred_at DESC);

CREATE INDEX IF NOT EXISTS audit_events_type_time_idx
    ON momna.audit_events (event_type, occurred_at DESC);
