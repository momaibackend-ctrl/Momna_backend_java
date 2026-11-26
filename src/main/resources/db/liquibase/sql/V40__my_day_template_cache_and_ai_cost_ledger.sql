-- MOMNA-849 / MOMNA-1325: the shared cache returns, now for anonymous day templates only.
-- A row is the validated model answer for one MyDayTemplateKey fingerprint (purpose, every prompt
-- input from a closed vocabulary, all versions) and one model policy. It has no user id, no Check-in
-- detail and no free text, so it may serve every user with the same key. TTL is at most 90 days;
-- losing the cache never touches a stored day.
CREATE TABLE momna.my_day_template_cache (
    template_fingerprint TEXT NOT NULL CHECK (template_fingerprint ~ '^[0-9a-f]{64}$'),
    model_policy TEXT NOT NULL,
    key_version TEXT NOT NULL,
    life_mode TEXT NOT NULL,
    language TEXT NOT NULL,
    prompts_version TEXT NOT NULL,
    model_ref TEXT NOT NULL,
    cards JSONB NOT NULL CHECK (jsonb_typeof(cards) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at AND expires_at <= created_at + INTERVAL '90 days'),
    PRIMARY KEY (template_fingerprint, model_policy)
);

CREATE INDEX idx_my_day_template_cache_expires ON momna.my_day_template_cache(expires_at);

-- What each AI call cost, for the per-request and per-subject budgets of the AI gateway.
-- The subject is a SHA-256 of the user id, never the id itself; no prompt or answer text is stored.
CREATE TABLE momna.ai_cost_ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    subject_hash TEXT NOT NULL CHECK (subject_hash ~ '^[0-9a-f]{64}$'),
    purpose TEXT NOT NULL,
    request_id TEXT NOT NULL,
    candidate_ref TEXT NOT NULL,
    input_tokens BIGINT NOT NULL CHECK (input_tokens >= 0),
    output_tokens BIGINT NOT NULL CHECK (output_tokens >= 0),
    cost_micros BIGINT NOT NULL CHECK (cost_micros >= 0),
    window_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ai_cost_ledger_window ON momna.ai_cost_ledger(subject_hash, purpose, window_key);
