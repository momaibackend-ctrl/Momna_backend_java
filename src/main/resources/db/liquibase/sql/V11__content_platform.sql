CREATE TABLE momna.content_objects (
    content_id VARCHAR(120) PRIMARY KEY,
    content_key VARCHAR(160) NOT NULL UNIQUE,
    content_type VARCHAR(16) NOT NULL CHECK (content_type IN ('TEXT','SLIDES','AUDIO')),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tags) = 'array'),
    created_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0)
);

CREATE TABLE momna.content_versions (
    content_id VARCHAR(120) NOT NULL REFERENCES momna.content_objects(content_id) ON DELETE RESTRICT,
    version INTEGER NOT NULL CHECK (version > 0),
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    content_type VARCHAR(16) NOT NULL CHECK (content_type IN ('TEXT','SLIDES','AUDIO')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED')),
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('EDITORIAL','IMPORTED','AI_GENERATED')),
    source_reference VARCHAR(240),
    variants JSONB NOT NULL CHECK (jsonb_typeof(variants) = 'array' AND jsonb_array_length(variants) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    PRIMARY KEY (content_id, version),
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
);

CREATE TABLE momna.content_operations (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    content_id VARCHAR(120) NOT NULL,
    content_version INTEGER NOT NULL,
    operation VARCHAR(24) NOT NULL CHECK (operation IN ('CREATE_DRAFT','PUBLISH','UNPUBLISH')),
    result_status VARCHAR(16) NOT NULL CHECK (result_status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (content_id, content_version) REFERENCES momna.content_versions(content_id, version) ON DELETE RESTRICT
);

CREATE INDEX idx_content_versions_effective ON momna.content_versions(content_id, status, version DESC);
CREATE INDEX idx_content_operations_content ON momna.content_operations(content_id, content_version);

COMMENT ON TABLE momna.content_objects IS 'Canonical logical ContentObject identity; Content Platform is the single writable owner.';
COMMENT ON TABLE momna.content_versions IS 'Immutable-by-contract versioned text/slides/audio metadata and localized variants; audio bytes remain in private object storage.';
COMMENT ON TABLE momna.content_operations IS 'Replay-safe authoring mutation idempotency records.';
