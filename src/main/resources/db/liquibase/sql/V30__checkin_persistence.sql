-- MOMNA E28 CI-B08: durable Check-in-owned state only.
-- Flow definitions/localization, Context Platform data, Safety catalog/rules and Identity remain owned elsewhere.
CREATE TABLE momna.checkin_sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    phase TEXT NOT NULL CHECK (phase IN ('MORNING','EVENING')),
    local_date DATE NOT NULL,
    period_at_time TEXT NOT NULL CHECK (period_at_time IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
    lifecycle_substage_at_time TEXT,
    timezone_at_session TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('DRAFT','PARTIAL','SUBMITTED','AUTO_FINALIZED')),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    definition_version INTEGER NOT NULL CHECK (definition_version > 0),
    rules_version TEXT NOT NULL,
    applicable_item_codes JSONB NOT NULL CHECK (jsonb_typeof(applicable_item_codes) = 'array'),
    completion_ratio NUMERIC(8,6) NOT NULL CHECK (completion_ratio >= 0 AND completion_ratio <= 1),
    safety_state TEXT NOT NULL CHECK (safety_state IN ('CLEAR','CLARIFICATION_REQUIRED','RESOLVED')),
    safety_decision_version TEXT,
    safety_route_code TEXT,
    safety_severity TEXT,
    safety_blocking BOOLEAN NOT NULL,
    safety_clarification JSONB,
    safety_allowed_actions JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(safety_allowed_actions) = 'array'),
    window_opens_at TIMESTAMPTZ NOT NULL,
    window_closes_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ,
    finalization_reason TEXT CHECK (finalization_reason IS NULL OR finalization_reason IN ('USER_SUBMIT','WINDOW_CLOSED')),
    consumed_at TIMESTAMPTZ,
    consumed_by_type TEXT CHECK (consumed_by_type IS NULL OR consumed_by_type = 'MY_DAY'),
    consumed_by_ref TEXT,
    CONSTRAINT uq_checkin_window UNIQUE (user_id, phase, local_date, period_at_time),
    CONSTRAINT uq_checkin_session_owner UNIQUE (session_id, user_id),
    CONSTRAINT ck_checkin_window_positive CHECK (window_opens_at < window_closes_at),
    CONSTRAINT ck_checkin_draft_ratio CHECK (status <> 'DRAFT' OR completion_ratio = 0),
    CONSTRAINT ck_checkin_partial_ratio CHECK (status <> 'PARTIAL' OR completion_ratio > 0),
    CONSTRAINT ck_checkin_finalization CHECK (
        (status IN ('DRAFT','PARTIAL') AND finalized_at IS NULL AND finalization_reason IS NULL AND submitted_at IS NULL)
        OR (status = 'SUBMITTED' AND finalized_at IS NOT NULL AND submitted_at IS NOT NULL AND finalization_reason = 'USER_SUBMIT')
        OR (status = 'AUTO_FINALIZED' AND finalized_at IS NOT NULL AND submitted_at IS NULL AND finalization_reason = 'WINDOW_CLOSED')
    ),
    CONSTRAINT ck_checkin_consumption CHECK (
        (consumed_at IS NULL AND consumed_by_type IS NULL AND consumed_by_ref IS NULL)
        OR (consumed_at IS NOT NULL AND consumed_by_type = 'MY_DAY' AND consumed_by_ref IS NOT NULL AND status IN ('SUBMITTED','AUTO_FINALIZED'))
    )
);

CREATE INDEX idx_checkin_sessions_user_day_phase
    ON momna.checkin_sessions(user_id, local_date, phase);
CREATE INDEX idx_checkin_sessions_consumed
    ON momna.checkin_sessions(user_id, consumed_at, local_date DESC)
    WHERE consumed_at IS NOT NULL;

CREATE TABLE momna.checkin_answers (
    session_id TEXT NOT NULL REFERENCES momna.checkin_sessions(session_id) ON DELETE CASCADE,
    item_code TEXT NOT NULL,
    value SMALLINT NOT NULL CHECK (value IN (0,1,2)),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, item_code)
);

CREATE TABLE momna.checkin_idempotency (
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    fingerprint_sha256 CHAR(64) NOT NULL CHECK (fingerprint_sha256 ~ '^[0-9a-f]{64}$'),
    result_session_id TEXT NOT NULL,
    result_revision BIGINT NOT NULL CHECK (result_revision >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, operation, idempotency_key),
    CONSTRAINT fk_checkin_idempotency_session_owner FOREIGN KEY (result_session_id, user_id)
        REFERENCES momna.checkin_sessions(session_id, user_id) ON DELETE RESTRICT
);
CREATE INDEX idx_checkin_idempotency_session
    ON momna.checkin_idempotency(user_id, result_session_id, created_at DESC);

CREATE TABLE momna.checkin_adjustment_signals (
    signal_id CHAR(64) PRIMARY KEY CHECK (signal_id ~ '^[0-9a-f]{64}$'),
    user_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    signal_code TEXT NOT NULL,
    source_phase TEXT NOT NULL CHECK (source_phase IN ('MORNING','EVENING')),
    local_date DATE NOT NULL,
    period_at_time TEXT NOT NULL CHECK (period_at_time IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
    item_code TEXT NOT NULL,
    state_points SMALLINT NOT NULL CHECK (state_points IN (0,1,2)),
    tier TEXT NOT NULL CHECK (tier IN ('NONE','MILD','MODERATE','STRONG')),
    score_contribution NUMERIC(20,6) NOT NULL CHECK (score_contribution > 0),
    completion_ratio NUMERIC(8,6) NOT NULL CHECK (completion_ratio >= 0 AND completion_ratio <= 1),
    effective_phase_weight NUMERIC(20,6),
    contributing_refs JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(contributing_refs) = 'array'),
    rules_version TEXT NOT NULL,
    definition_version INTEGER NOT NULL CHECK (definition_version > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_checkin_signal_session_owner FOREIGN KEY (session_id, user_id)
        REFERENCES momna.checkin_sessions(session_id, user_id) ON DELETE CASCADE
);
CREATE INDEX idx_checkin_adjustment_signals_lookup
    ON momna.checkin_adjustment_signals(user_id, local_date, source_phase, session_id, created_at);

COMMENT ON TABLE momna.checkin_sessions IS 'CI-B08 durable Check-in session state using only canonical E28 finalization states.';
COMMENT ON TABLE momna.checkin_answers IS 'Only explicit user selections are rows. Missing answer is absence, never implicit zero.';
COMMENT ON TABLE momna.checkin_adjustment_signals IS 'Durable CI-B06 adjustment evidence. Flow definitions and weighting source-of-truth remain outside persistence.';
