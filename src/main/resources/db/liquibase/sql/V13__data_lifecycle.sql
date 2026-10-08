CREATE TABLE momna.data_lifecycle_policies (
  policy_key VARCHAR(120) NOT NULL,
  policy_version INTEGER NOT NULL CHECK (policy_version > 0),
  owner VARCHAR(160) NOT NULL,
  resource_type VARCHAR(160) NOT NULL,
  privacy_scope VARCHAR(40) NOT NULL,
  retention_mode VARCHAR(16) NOT NULL CHECK (retention_mode IN ('WINDOW','INDEFINITE')),
  retain_for_seconds BIGINT CHECK (retain_for_seconds IS NULL OR retain_for_seconds > 0),
  archive_after_seconds BIGINT CHECK (archive_after_seconds IS NULL OR archive_after_seconds > 0),
  delete_after_seconds BIGINT CHECK (delete_after_seconds IS NULL OR delete_after_seconds > 0),
  archive_allowed BOOLEAN NOT NULL,
  restore_allowed BOOLEAN NOT NULL,
  export_allowed BOOLEAN NOT NULL,
  deletion_allowed BOOLEAN NOT NULL,
  deletion_action VARCHAR(24) NOT NULL CHECK (deletion_action IN ('DELETE','ANONYMIZE','RETAIN_REQUIRED','ARCHIVE_REQUIRED')),
  legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
  row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  PRIMARY KEY(policy_key,policy_version),
  UNIQUE(owner,resource_type,policy_version),
  CHECK (retention_mode <> 'WINDOW' OR retain_for_seconds IS NOT NULL)
);

CREATE TABLE momna.data_lifecycle_operations (
  operation_id UUID PRIMARY KEY,
  subject_user_id VARCHAR(160) NOT NULL,
  actor_user_id VARCHAR(160) NOT NULL,
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('ARCHIVE','RESTORE','EXPORT','DELETION')),
  state VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','DEAD_LETTER')),
  idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  trace_id VARCHAR(160) NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL,
  resource_owner VARCHAR(160), resource_type VARCHAR(160), resource_id VARCHAR(160), privacy_scope VARCHAR(40),
  retention_anchor_at TIMESTAMPTZ, valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
  timezone_at_event VARCHAR(80), local_date_at_event DATE,
  content_id VARCHAR(120), content_version INTEGER, content_schema_version INTEGER,
  deletion_plan_id UUID, related_operation_id UUID,
  policy_key VARCHAR(120), policy_version INTEGER,
  artifact_bucket VARCHAR(120), artifact_key VARCHAR(400), artifact_content_type VARCHAR(120), artifact_size_bytes BIGINT,
  timezone_at_request VARCHAR(80), local_date_at_request DATE,
  error_code VARCHAR(80), row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK ((resource_owner IS NULL AND resource_type IS NULL AND resource_id IS NULL AND privacy_scope IS NULL AND retention_anchor_at IS NULL) OR (resource_owner IS NOT NULL AND resource_type IS NOT NULL AND resource_id IS NOT NULL AND privacy_scope IS NOT NULL AND retention_anchor_at IS NOT NULL)),
  CHECK ((content_id IS NULL AND content_version IS NULL AND content_schema_version IS NULL) OR (content_id IS NOT NULL AND content_version > 0 AND content_schema_version > 0)),
  CHECK ((policy_key IS NULL AND policy_version IS NULL) OR (policy_key IS NOT NULL AND policy_version > 0)),
  CHECK ((artifact_bucket IS NULL AND artifact_key IS NULL AND artifact_content_type IS NULL AND artifact_size_bytes IS NULL) OR (artifact_bucket IS NOT NULL AND artifact_key IS NOT NULL AND artifact_content_type IS NOT NULL AND artifact_size_bytes >= 0))
);

CREATE TABLE momna.data_lifecycle_deletion_plans (
  plan_id UUID PRIMARY KEY, subject_user_id VARCHAR(160) NOT NULL, actor_user_id VARCHAR(160) NOT NULL,
  snapshot_at TIMESTAMPTZ NOT NULL, contract_version VARCHAR(80) NOT NULL, idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  trace_id VARCHAR(160) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE momna.data_lifecycle_deletion_plan_actions (
  plan_id UUID NOT NULL REFERENCES momna.data_lifecycle_deletion_plans(plan_id) ON DELETE CASCADE, action_index INTEGER NOT NULL CHECK(action_index>=0),
  resource_owner VARCHAR(160) NOT NULL, resource_type VARCHAR(160) NOT NULL, resource_id VARCHAR(160) NOT NULL, subject_user_id VARCHAR(160) NOT NULL,
  privacy_scope VARCHAR(40) NOT NULL, retention_anchor_at TIMESTAMPTZ NOT NULL, valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
  timezone_at_event VARCHAR(80), local_date_at_event DATE, content_id VARCHAR(120), content_version INTEGER, content_schema_version INTEGER,
  action VARCHAR(24) NOT NULL CHECK(action IN ('DELETE','ANONYMIZE','RETAIN_REQUIRED','ARCHIVE_REQUIRED')), reason_code VARCHAR(80) NOT NULL,
  policy_key VARCHAR(120) NOT NULL, policy_version INTEGER NOT NULL CHECK(policy_version>0), PRIMARY KEY(plan_id,action_index)
);
CREATE TABLE momna.data_lifecycle_archive_manifests (
  operation_id UUID PRIMARY KEY REFERENCES momna.data_lifecycle_operations(operation_id) ON DELETE RESTRICT,
  policy_key VARCHAR(120) NOT NULL, policy_version INTEGER NOT NULL CHECK(policy_version>0),
  artifact_bucket VARCHAR(120) NOT NULL, artifact_key VARCHAR(400) NOT NULL, artifact_content_type VARCHAR(120) NOT NULL, artifact_size_bytes BIGINT NOT NULL CHECK(artifact_size_bytes>=0), created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE momna.data_lifecycle_export_manifests (
  operation_id UUID PRIMARY KEY REFERENCES momna.data_lifecycle_operations(operation_id) ON DELETE RESTRICT,
  subject_user_id VARCHAR(160) NOT NULL, snapshot_at TIMESTAMPTZ NOT NULL, schema_version INTEGER NOT NULL CHECK(schema_version>0), included_owners TEXT NOT NULL,
  included_resource_count INTEGER NOT NULL CHECK(included_resource_count>=0), excluded_resource_count INTEGER NOT NULL CHECK(excluded_resource_count>=0),
  artifact_bucket VARCHAR(120) NOT NULL, artifact_key VARCHAR(400) NOT NULL, artifact_content_type VARCHAR(120) NOT NULL, artifact_size_bytes BIGINT NOT NULL CHECK(artifact_size_bytes>=0), created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE momna.data_lifecycle_deletion_receipts (
  operation_id UUID PRIMARY KEY REFERENCES momna.data_lifecycle_operations(operation_id) ON DELETE RESTRICT,
  plan_id UUID NOT NULL REFERENCES momna.data_lifecycle_deletion_plans(plan_id) ON DELETE RESTRICT, subject_user_id VARCHAR(160) NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL, applied_action_count INTEGER NOT NULL CHECK(applied_action_count>=0)
);
CREATE TABLE momna.data_lifecycle_retained_exceptions (
  operation_id UUID NOT NULL REFERENCES momna.data_lifecycle_deletion_receipts(operation_id) ON DELETE CASCADE, exception_index INTEGER NOT NULL CHECK(exception_index>=0),
  resource_owner VARCHAR(160) NOT NULL, resource_type VARCHAR(160) NOT NULL, resource_id VARCHAR(160) NOT NULL, subject_user_id VARCHAR(160) NOT NULL,
  privacy_scope VARCHAR(40) NOT NULL, retention_anchor_at TIMESTAMPTZ NOT NULL, valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
  timezone_at_event VARCHAR(80), local_date_at_event DATE, content_id VARCHAR(120), content_version INTEGER, content_schema_version INTEGER,
  reason_code VARCHAR(80) NOT NULL, policy_key VARCHAR(120) NOT NULL, policy_version INTEGER NOT NULL CHECK(policy_version>0), PRIMARY KEY(operation_id,exception_index)
);
CREATE TABLE momna.data_lifecycle_checkpoints (
  operation_id UUID NOT NULL REFERENCES momna.data_lifecycle_operations(operation_id) ON DELETE RESTRICT,
  owner VARCHAR(160) NOT NULL, resource_type VARCHAR(160) NOT NULL, resource_id VARCHAR(160) NOT NULL, action VARCHAR(40) NOT NULL,
  result_code VARCHAR(80) NOT NULL, updated_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(operation_id,owner,resource_type,resource_id,action)
);
CREATE INDEX idx_data_lifecycle_policy_owner ON momna.data_lifecycle_policies(owner,resource_type,policy_version DESC);
CREATE INDEX idx_data_lifecycle_operation_subject ON momna.data_lifecycle_operations(subject_user_id,requested_at DESC);
COMMENT ON TABLE momna.data_lifecycle_policies IS 'Versioned lifecycle policy data. Product-specific retention durations are not hard-coded in services.';
COMMENT ON TABLE momna.data_lifecycle_operations IS 'Technical lifecycle operation state only; exported/archive payload values remain in private object storage.';
