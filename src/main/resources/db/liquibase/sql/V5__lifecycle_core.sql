-- CORE-BE-06 canonical Lifecycle Core + first PostgreSQL adapter for the shared TransactionalOutboxContract.
CREATE TABLE IF NOT EXISTS momna.lifecycle_period_history (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    period TEXT NOT NULL CHECK (period IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
    substage TEXT,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    source TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    selected_manually BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_to > effective_from),
    CHECK (length(trim(source)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lifecycle_one_open_primary
    ON momna.lifecycle_period_history(user_id) WHERE effective_to IS NULL;
CREATE INDEX IF NOT EXISTS idx_lifecycle_history_user_time
    ON momna.lifecycle_period_history(user_id, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS momna.lifecycle_contexts (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    context_type TEXT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    source TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (length(trim(context_type)) > 0),
    CHECK (length(trim(source)) > 0)
);
CREATE INDEX IF NOT EXISTS idx_lifecycle_contexts_user_window
    ON momna.lifecycle_contexts(user_id, valid_from, valid_to);

CREATE TABLE IF NOT EXISTS momna.lifecycle_transitions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    from_period TEXT CHECK (from_period IS NULL OR from_period IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
    from_substage TEXT,
    to_period TEXT NOT NULL CHECK (to_period IN ('MENARCHE','CYCLE','PLANNING','PREGNANCY','POSTPARTUM','PERIMENOPAUSE','MENOPAUSE')),
    to_substage TEXT,
    reason TEXT NOT NULL,
    confirmation_state TEXT NOT NULL CHECK (confirmation_state IN ('PROPOSED','DISPUTED','CONFIRMED','REJECTED')),
    rule_version TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    selected_manually BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (length(trim(reason)) > 0),
    CHECK (length(trim(rule_version)) > 0),
    CHECK (length(trim(source)) > 0)
);
CREATE INDEX IF NOT EXISTS idx_lifecycle_transitions_user_time
    ON momna.lifecycle_transitions(user_id, occurred_at, id);

-- Shared, domain-neutral outbox backing the pre-existing core TransactionalOutboxContract.
CREATE TABLE IF NOT EXISTS momna.transactional_outbox (
    event_id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    CHECK (length(trim(event_type)) > 0)
);
CREATE INDEX IF NOT EXISTS idx_transactional_outbox_pending
    ON momna.transactional_outbox(created_at, event_id) WHERE published_at IS NULL;

COMMENT ON TABLE momna.lifecycle_period_history IS 'CORE-BE-06 append-preserving primary lifecycle history; current state is a projection.';
COMMENT ON TABLE momna.lifecycle_contexts IS 'CORE-BE-06 additional lifecycle contexts that coexist with one primary period.';
COMMENT ON TABLE momna.lifecycle_transitions IS 'CORE-BE-06 versioned proposed/disputed/confirmed lifecycle transitions.';
COMMENT ON TABLE momna.transactional_outbox IS 'Shared transactional outbox implementation for core domain events; not lifecycle-specific.';
