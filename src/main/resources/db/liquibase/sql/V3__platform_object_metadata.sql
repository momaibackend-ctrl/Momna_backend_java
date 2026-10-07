create table if not exists momna.platform_object_metadata (
    bucket varchar(128) not null,
    object_key text not null,
    owner_id varchar(128) not null,
    content_type varchar(255) not null,
    size_bytes bigint not null check (size_bytes >= 0),
    sensitivity varchar(32) not null check (sensitivity in ('PRIVATE','MEDICAL_PRIVATE')),
    created_at timestamptz not null default now(),
    primary key(bucket, object_key)
);
create index if not exists idx_platform_object_metadata_owner on momna.platform_object_metadata(owner_id,created_at);
