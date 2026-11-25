-- MOMNA-1145: durable v2.2 medical safety route state and the audit of corrected answers.
-- Product rules stay versioned in the catalog; clinical release approval is a separate gate.
CREATE TABLE momna.medical_safety_route_states (
    route_state_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    route_id TEXT NOT NULL,
    session_id TEXT REFERENCES momna.checkin_sessions(session_id) ON DELETE SET NULL,
    source_item TEXT,
    state TEXT NOT NULL CHECK (state IN ('QUESTION', 'COMPLETE')),
    fact_answers JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(fact_answers) = 'object'),
    completed_at TIMESTAMPTZ,
    cooldown_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    reset_observed BOOLEAN NOT NULL DEFAULT FALSE,
    disposition TEXT,
    screen_code TEXT,
    action_code TEXT,
    bypass_reason TEXT,
    condition_status TEXT,
    rule_version TEXT NOT NULL,
    catalog_version TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_medical_safety_user_route UNIQUE (user_id, route_id)
);
CREATE INDEX idx_medical_safety_session
    ON momna.medical_safety_route_states(user_id, session_id);
CREATE INDEX idx_medical_safety_cooldown
    ON momna.medical_safety_route_states(user_id, cooldown_until);
COMMENT ON TABLE momna.medical_safety_route_states IS
    'Private, durable v2.2 medical route state. Never included in couple-shared payloads.';

CREATE TABLE momna.medical_safety_fact_revisions (
    revision_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    route_id TEXT NOT NULL,
    session_id TEXT REFERENCES momna.checkin_sessions(session_id) ON DELETE SET NULL,
    fact_code TEXT NOT NULL,
    previous_answer TEXT NOT NULL CHECK (previous_answer IN ('YES','NO','UNSURE','ABSENT')),
    new_answer TEXT NOT NULL CHECK (new_answer IN ('YES','NO','UNSURE','ABSENT')),
    revised_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_medical_fact_revision_user_route
    ON momna.medical_safety_fact_revisions(user_id, route_id, revised_at DESC);
COMMENT ON TABLE momna.medical_safety_fact_revisions IS
    'Sensitive Safety change history. Access is restricted to user-owned medical context.';
