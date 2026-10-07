CREATE TABLE IF NOT EXISTS momna.auth_accounts (
    user_id text PRIMARY KEY,
    status text NOT NULL CHECK (status IN ('ACTIVE','DISABLED','DELETED')),
    created_at timestamptz NOT NULL,
    disabled_at timestamptz NULL
);

CREATE TABLE IF NOT EXISTS momna.login_identities (
    identity_id text PRIMARY KEY,
    user_id text NOT NULL REFERENCES momna.auth_accounts(user_id),
    provider text NOT NULL CHECK (provider IN ('APPLE','GOOGLE','EMAIL')),
    provider_subject text NOT NULL,
    verified_email text NULL,
    created_at timestamptz NOT NULL,
    UNIQUE(provider, provider_subject)
);
CREATE INDEX IF NOT EXISTS login_identities_user_idx ON momna.login_identities(user_id);

CREATE TABLE IF NOT EXISTS momna.auth_sessions (
    session_id text PRIMARY KEY,
    family_id text NOT NULL,
    user_id text NOT NULL REFERENCES momna.auth_accounts(user_id),
    access_hash text NOT NULL UNIQUE,
    refresh_hash text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    authenticated_at timestamptz NOT NULL,
    access_expires_at timestamptz NOT NULL,
    refresh_expires_at timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    device_label text NULL,
    replaced_by_session_id text NULL,
    revoked_at timestamptz NULL
);
CREATE INDEX IF NOT EXISTS auth_sessions_user_idx ON momna.auth_sessions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS auth_sessions_family_idx ON momna.auth_sessions(family_id);

CREATE TABLE IF NOT EXISTS momna.auth_refresh_history (
    refresh_hash text PRIMARY KEY,
    family_id text NOT NULL,
    used_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS momna.auth_email_challenges (
    challenge_id text PRIMARY KEY,
    email_hash text NOT NULL,
    code_hash text NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz NULL
);
CREATE INDEX IF NOT EXISTS auth_email_challenges_expiry_idx ON momna.auth_email_challenges(expires_at);

COMMENT ON TABLE momna.auth_sessions IS 'CORE-BE-26 server-controlled sessions. Only SHA-256 hashes of opaque credentials are persisted; plaintext credentials are forbidden.';
COMMENT ON TABLE momna.auth_email_challenges IS 'Passwordless email challenges; email and one-time code are persisted only as hashes.';
