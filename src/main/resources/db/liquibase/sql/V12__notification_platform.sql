CREATE TABLE momna.notification_schedules (
    schedule_id UUID PRIMARY KEY,
    user_id VARCHAR(160) NOT NULL,
    content_key VARCHAR(160) NOT NULL,
    pinned_content_version INTEGER CHECK (pinned_content_version IS NULL OR pinned_content_version > 0),
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('PUSH','EMAIL','IN_APP')),
    category VARCHAR(80) NOT NULL,
    purpose VARCHAR(120) NOT NULL,
    priority VARCHAR(16) NOT NULL CHECK (priority IN ('NORMAL','HIGH','CRITICAL')),
    safety_signal_keys TEXT NOT NULL DEFAULT '',
    start_local_date DATE NOT NULL,
    local_time TIME NOT NULL,
    timezone_mode VARCHAR(20) NOT NULL CHECK (timezone_mode IN ('FOLLOW_PROFILE','PINNED')),
    pinned_timezone VARCHAR(80),
    recurrence VARCHAR(16) NOT NULL CHECK (recurrence IN ('ONCE','DAILY')),
    ambiguous_policy VARCHAR(20) NOT NULL CHECK (ambiguous_policy IN ('REJECT','EARLIER_OFFSET','LATER_OFFSET')),
    nonexistent_policy VARCHAR(20) NOT NULL CHECK (nonexistent_policy IN ('REJECT','SHIFT_FORWARD')),
    retry_max_attempts INTEGER NOT NULL CHECK (retry_max_attempts BETWEEN 1 AND 10),
    retry_initial_backoff_seconds BIGINT NOT NULL CHECK (retry_initial_backoff_seconds > 0),
    retry_policy_version VARCHAR(80) NOT NULL,
    next_delivery_at TIMESTAMPTZ,
    state VARCHAR(16) NOT NULL CHECK (state IN ('ACTIVE','CANCELLED','COMPLETED')),
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    CHECK ((timezone_mode='PINNED' AND pinned_timezone IS NOT NULL) OR (timezone_mode='FOLLOW_PROFILE' AND pinned_timezone IS NULL))
);

CREATE TABLE momna.notification_preferences (
    user_id VARCHAR(160) NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('PUSH','EMAIL','IN_APP')),
    category VARCHAR(80) NOT NULL,
    purpose VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    quiet_start TIME,
    quiet_end TIME,
    quiet_policy_version VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY(user_id,channel,category,purpose),
    CHECK ((quiet_start IS NULL AND quiet_end IS NULL AND quiet_policy_version IS NULL) OR (quiet_start IS NOT NULL AND quiet_end IS NOT NULL AND quiet_policy_version IS NOT NULL AND quiet_start <> quiet_end))
);

CREATE TABLE momna.notification_delivery_attempts (
    attempt_id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES momna.notification_schedules(schedule_id) ON DELETE RESTRICT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED','ENQUEUED','RETRY_WAIT','DELIVERED','FAILED','DEAD_LETTER')),
    content_id VARCHAR(120) NOT NULL,
    content_key VARCHAR(160) NOT NULL,
    content_version INTEGER NOT NULL CHECK (content_version > 0),
    content_schema_version INTEGER NOT NULL CHECK (content_schema_version > 0),
    resolved_locale VARCHAR(40) NOT NULL,
    locale_policy_version VARCHAR(80) NOT NULL,
    country_policy_version VARCHAR(80),
    retry_policy_version VARCHAR(80) NOT NULL,
    provider_neutral_code VARCHAR(80),
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(schedule_id,scheduled_at,attempt_number)
);

CREATE TABLE momna.notification_operations (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    operation VARCHAR(40) NOT NULL,
    entity_ref VARCHAR(400) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_schedules_due ON momna.notification_schedules(state,next_delivery_at) WHERE next_delivery_at IS NOT NULL;
CREATE INDEX idx_notification_attempts_retry_due ON momna.notification_delivery_attempts(status,next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_notification_attempts_schedule ON momna.notification_delivery_attempts(schedule_id,scheduled_at,attempt_number);

COMMENT ON TABLE momna.notification_schedules IS 'Canonical Notification Platform schedules. UTC next_delivery_at is derived from local clock + canonical IANA timezone metadata.';
COMMENT ON TABLE momna.notification_preferences IS 'Canonical channel/category/purpose preferences and local quiet-hours policy.';
COMMENT ON TABLE momna.notification_delivery_attempts IS 'Provider-neutral delivery evidence and bounded retry/dead-letter state. Message bodies and destinations are intentionally absent.';
COMMENT ON TABLE momna.notification_operations IS 'Replay-safe notification idempotency ledger.';
