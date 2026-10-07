-- E28 Check-in owned durable state. Missing answer is absence; explicit 0 is a stored row.
CREATE TABLE momna.checkin_sessions (
 session_id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
 phase TEXT NOT NULL CHECK (phase IN ('MORNING','EVENING')),
 local_date DATE NOT NULL,
 period_at_time TEXT NOT NULL CHECK (period_at_time IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
 lifecycle_substage_at_time TEXT,
 timezone_at_session TEXT NOT NULL,
 status TEXT NOT NULL CHECK (status IN ('DRAFT','PARTIAL','SUBMITTED','AUTO_FINALIZED')),
 revision BIGINT NOT NULL CHECK (revision>=0), definition_version INTEGER NOT NULL CHECK(definition_version>0), rules_version TEXT NOT NULL,
 applicable_item_codes JSONB NOT NULL CHECK(jsonb_typeof(applicable_item_codes)='array'), completion_ratio NUMERIC(8,6) NOT NULL CHECK(completion_ratio>=0 AND completion_ratio<=1),
 safety_state TEXT NOT NULL CHECK(safety_state IN ('CLEAR','CLARIFICATION_REQUIRED','RESOLVED')), safety_decision_version TEXT, safety_route_code TEXT, safety_severity TEXT, safety_blocking BOOLEAN NOT NULL, safety_clarification JSONB, safety_allowed_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
 window_opens_at TIMESTAMPTZ NOT NULL, window_closes_at TIMESTAMPTZ NOT NULL, started_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 submitted_at TIMESTAMPTZ, finalized_at TIMESTAMPTZ, finalization_reason TEXT CHECK(finalization_reason IS NULL OR finalization_reason IN ('USER_SUBMIT','WINDOW_CLOSED')),
 consumed_at TIMESTAMPTZ, consumed_by_type TEXT CHECK(consumed_by_type IS NULL OR consumed_by_type='MY_DAY'), consumed_by_ref TEXT,
 CONSTRAINT uq_checkin_window UNIQUE(user_id,phase,local_date,period_at_time), CONSTRAINT uq_checkin_session_owner UNIQUE(session_id,user_id), CONSTRAINT ck_checkin_window_positive CHECK(window_opens_at<window_closes_at)
);
CREATE TABLE momna.checkin_answers(session_id TEXT NOT NULL REFERENCES momna.checkin_sessions(session_id) ON DELETE CASCADE,item_code TEXT NOT NULL,value SMALLINT NOT NULL CHECK(value IN(0,1,2)),updated_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(session_id,item_code));
CREATE TABLE momna.checkin_idempotency(user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,operation TEXT NOT NULL,idempotency_key TEXT NOT NULL,fingerprint_sha256 CHAR(64) NOT NULL,result_session_id TEXT NOT NULL,result_revision BIGINT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(user_id,operation,idempotency_key));
CREATE TABLE momna.checkin_adjustment_signals(signal_id CHAR(64) PRIMARY KEY,user_id TEXT NOT NULL,session_id TEXT NOT NULL,item_code TEXT NOT NULL,signal_code TEXT NOT NULL,source_phase TEXT NOT NULL,local_date DATE NOT NULL,period_at_time TEXT NOT NULL,state_points SMALLINT NOT NULL CHECK(state_points IN(0,1,2)),tier TEXT NOT NULL,score_contribution NUMERIC(20,6) NOT NULL,completion_ratio NUMERIC(8,6) NOT NULL,effective_phase_weight NUMERIC(20,6),rules_version TEXT NOT NULL,definition_version INTEGER NOT NULL,created_at TIMESTAMPTZ NOT NULL,CONSTRAINT fk_checkin_signal_session_owner FOREIGN KEY(session_id,user_id) REFERENCES momna.checkin_sessions(session_id,user_id) ON DELETE CASCADE);
