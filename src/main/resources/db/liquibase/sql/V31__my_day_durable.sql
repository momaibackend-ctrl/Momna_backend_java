-- MOMNA-106 MYDAY-BE-01: the durable personal My Day artifact and its card progress.
-- PostgreSQL is the source of truth; caches are an optimisation only.
-- My Day owns the day, its cards and their progress; Check-in, Context, Safety and AI stay elsewhere.
CREATE TABLE momna.my_days (
    my_day_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES momna.user_profiles(user_id) ON DELETE RESTRICT,
    local_date DATE NOT NULL,
    timezone_at_generation TEXT NOT NULL,
    lifecycle_period TEXT NOT NULL CHECK (lifecycle_period IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
    language TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE')),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    orchestration_id TEXT NOT NULL,
    orchestration_version TEXT NOT NULL,
    baseline_share NUMERIC(8,6) NOT NULL CHECK (baseline_share >= 0 AND baseline_share <= 1),
    provenance JSONB NOT NULL CHECK (jsonb_typeof(provenance) = 'object'),
    card_source TEXT NOT NULL,
    generation_policy_version TEXT NOT NULL,
    prompt_version TEXT,
    model_ref TEXT,
    safety_version TEXT,
    cache_key TEXT,
    generated_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_my_day_user_date UNIQUE (user_id, local_date),
    CONSTRAINT uq_my_day_owner UNIQUE (my_day_id, user_id)
);

CREATE TABLE momna.my_day_cards (
    my_day_id TEXT NOT NULL REFERENCES momna.my_days(my_day_id) ON DELETE CASCADE,
    card_id TEXT NOT NULL,
    slot TEXT NOT NULL CHECK (slot IN ('AM','PM','OPTIONAL')),
    position INTEGER NOT NULL CHECK (position BETWEEN 1 AND 3),
    title TEXT NOT NULL,
    microcopy TEXT NOT NULL,
    what_to_do TEXT NOT NULL,
    why_today TEXT NOT NULL,
    how_to_do TEXT NOT NULL,
    if_skipped TEXT NOT NULL,
    support TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING','COMPLETED','SKIPPED')),
    status_changed_at TIMESTAMPTZ,
    PRIMARY KEY (my_day_id, card_id),
    CONSTRAINT uq_my_day_card_slot UNIQUE (my_day_id, slot, position),
    CONSTRAINT ck_my_day_card_status_time CHECK ((status = 'PENDING') = (status_changed_at IS NULL))
);

CREATE TABLE momna.my_day_idempotency (
    user_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    fingerprint_sha256 TEXT NOT NULL CHECK (length(fingerprint_sha256) = 64),
    result_my_day_id TEXT NOT NULL REFERENCES momna.my_days(my_day_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, operation, idempotency_key)
);

CREATE INDEX idx_my_days_user_date ON momna.my_days(user_id, local_date DESC);
