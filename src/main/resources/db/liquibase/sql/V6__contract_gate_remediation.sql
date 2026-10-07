-- CORE-BE-01..06 contract remediation: additive concurrency/job-contract fields only.
ALTER TABLE momna.user_profiles
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE momna.platform_jobs
    ADD COLUMN IF NOT EXISTS payload_schema_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS result_ref TEXT,
    ADD COLUMN IF NOT EXISTS failure_code TEXT,
    ADD COLUMN IF NOT EXISTS failure_retryable BOOLEAN,
    ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE momna.platform_jobs DROP CONSTRAINT IF EXISTS platform_jobs_status_check;
ALTER TABLE momna.platform_jobs
    ADD CONSTRAINT platform_jobs_status_check
    CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','RETRY_WAIT','FAILED','DEAD_LETTER','CANCELLED'));

ALTER TABLE momna.platform_jobs DROP CONSTRAINT IF EXISTS platform_jobs_payload_schema_version_check;
ALTER TABLE momna.platform_jobs
    ADD CONSTRAINT platform_jobs_payload_schema_version_check CHECK (payload_schema_version >= 1);

COMMENT ON COLUMN momna.user_profiles.version IS 'Optimistic concurrency version owned by Identity/Profile Core.';
COMMENT ON COLUMN momna.platform_jobs.payload_schema_version IS 'Version of the typed job payload contract.';
COMMENT ON COLUMN momna.platform_jobs.result_ref IS 'Provider-neutral result/reference emitted on successful completion.';
