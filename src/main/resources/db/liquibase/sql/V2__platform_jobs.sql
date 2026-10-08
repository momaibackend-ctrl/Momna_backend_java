create table if not exists momna.platform_jobs (
    id uuid primary key,
    job_type varchar(64) not null,
    payload_ref text not null,
    idempotency_key varchar(200) not null unique,
    status varchar(32) not null check (status in ('QUEUED','RUNNING','SUCCEEDED','RETRY_WAIT','DEAD_LETTER')),
    attempts integer not null default 0 check (attempts >= 0),
    max_attempts integer not null check (max_attempts >= 1),
    retry_backoff_ms bigint not null check (retry_backoff_ms > 0),
    trace_id varchar(128) not null,
    worker_id varchar(128),
    retry_at timestamptz,
    lease_expires_at timestamptz,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);
create index if not exists idx_platform_jobs_claim on momna.platform_jobs(status,retry_at,lease_expires_at,created_at);
