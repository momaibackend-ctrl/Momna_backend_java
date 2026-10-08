-- CORE-BE-05 canonical product profile and append-only consent history.
-- Account/auth identity remains outside this product-profile table; user_id is the stable boundary reference.
CREATE TABLE IF NOT EXISTS momna.user_profiles (
    user_id TEXT PRIMARY KEY,
    display_name TEXT,
    birth_date DATE,
    preferred_language TEXT,
    locale TEXT,
    country_region TEXT,
    timezone TEXT,
    measurement_system TEXT,
    unit_preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    notification_preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    account_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS momna.consent_records (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    consent_type TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('GRANTED', 'WITHDRAWN')),
    recorded_at TIMESTAMPTZ NOT NULL,
    source TEXT NOT NULL,
    CHECK (length(trim(consent_type)) > 0),
    CHECK (length(trim(policy_version)) > 0),
    CHECK (length(trim(source)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_consent_records_user_time
    ON momna.consent_records(user_id, recorded_at, id);

COMMENT ON TABLE momna.user_profiles IS 'CORE-BE-05 single canonical Momna product profile; feature modules must not duplicate this state.';
COMMENT ON TABLE momna.consent_records IS 'CORE-BE-05 append-only versioned consent events; withdrawal appends history and never overwrites a grant.';
