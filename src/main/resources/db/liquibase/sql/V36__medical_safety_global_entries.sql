-- MOMNA-1145: the global v2.2 entries (ER0, WB1, calendar signals).
-- The lifecycle period a global entry ran in decides its screen (e.g. OB1 in pregnancy).
ALTER TABLE momna.medical_safety_route_states ADD COLUMN lifecycle_period TEXT;

-- One optional postpartum wellbeing invitation; never repeated, also after "I'd rather not say".
CREATE TABLE momna.medical_safety_wb1_responses (
    user_id TEXT PRIMARY KEY REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    answer TEXT NOT NULL CHECK (answer IN ('MOSTLY_GOOD','HARD_LATELY','PREFER_NOT_TO_SAY')),
    catalog_version TEXT NOT NULL,
    responded_at TIMESTAMPTZ NOT NULL
);
COMMENT ON TABLE momna.medical_safety_wb1_responses IS
    'WB1 private response: a one-time invitation to support, not depression screening. Never shared (Couple Mode).';

-- Replays of the global safety actions: the same key and request returns the stored response.
CREATE TABLE momna.medical_safety_idempotency (
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    fingerprint_sha256 TEXT NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, operation, idempotency_key)
);
COMMENT ON TABLE momna.medical_safety_idempotency IS
    'Private replay store of global medical safety actions; holds route steps, never free text.';
