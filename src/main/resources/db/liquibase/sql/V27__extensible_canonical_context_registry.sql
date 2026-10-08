-- CORE-BE-24: versioned runtime registry + exact-definition-pinned canonical fact envelope.
ALTER TABLE momna.canonical_field_definitions ADD COLUMN IF NOT EXISTS namespace TEXT;
UPDATE momna.canonical_field_definitions SET namespace = split_part(replace(field_id, ':', '.'), '.', 1) WHERE namespace IS NULL;
ALTER TABLE momna.canonical_field_definitions ALTER COLUMN namespace SET NOT NULL;
ALTER TABLE momna.canonical_field_definitions ADD COLUMN IF NOT EXISTS schema_version INTEGER;
UPDATE momna.canonical_field_definitions SET schema_version = definition_version WHERE schema_version IS NULL;
ALTER TABLE momna.canonical_field_definitions ALTER COLUMN schema_version SET NOT NULL;
ALTER TABLE momna.canonical_field_definitions ADD COLUMN IF NOT EXISTS registry_policy JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE momna.canonical_field_definitions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS momna.canonical_field_definition_versions (
    field_id TEXT NOT NULL,
    definition_version INTEGER NOT NULL CHECK (definition_version > 0),
    namespace TEXT NOT NULL,
    data_type TEXT NOT NULL CHECK (data_type IN ('ENUM','DATE','DATE_TIME','NUMBER','TEXT','BOOLEAN','OBJECT','RECORD','COLLECTION')),
    domain_owner TEXT NOT NULL,
    sensitivity_class TEXT NOT NULL,
    validation_schema JSONB NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    registry_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    deprecated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(field_id, definition_version)
);

INSERT INTO momna.canonical_field_definition_versions(field_id,definition_version,namespace,data_type,domain_owner,sensitivity_class,validation_schema,schema_version,registry_policy,deprecated_at,created_at,updated_at)
SELECT field_id,definition_version,namespace,data_type,domain_owner,sensitivity_class,validation_schema,schema_version,registry_policy,deprecated_at,created_at,updated_at
FROM momna.canonical_field_definitions
ON CONFLICT (field_id, definition_version) DO NOTHING;

ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS definition_version INTEGER;
UPDATE momna.canonical_field_values v SET definition_version = d.definition_version FROM momna.canonical_field_definitions d WHERE v.field_id=d.field_id AND v.definition_version IS NULL;
ALTER TABLE momna.canonical_field_values ALTER COLUMN definition_version SET NOT NULL;
ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS schema_version INTEGER;
UPDATE momna.canonical_field_values v SET schema_version = d.schema_version FROM momna.canonical_field_definitions d WHERE v.field_id=d.field_id AND v.schema_version IS NULL;
ALTER TABLE momna.canonical_field_values ALTER COLUMN schema_version SET NOT NULL;
ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS knowledge_state TEXT NOT NULL DEFAULT 'KNOWN';
ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS privacy_classification TEXT;
ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS consent_scope TEXT;
ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS sensitivity_classification TEXT;
ALTER TABLE momna.canonical_field_values ADD COLUMN IF NOT EXISTS applied_merge_policy TEXT;
UPDATE momna.canonical_field_values v SET privacy_classification=d.sensitivity_class, consent_scope=d.sensitivity_class, sensitivity_classification=d.sensitivity_class, applied_merge_policy='LATEST' FROM momna.canonical_field_definitions d WHERE v.field_id=d.field_id AND v.privacy_classification IS NULL;

DO $$ DECLARE c RECORD; BEGIN
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='momna.canonical_field_values'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%typed_value%reference_id%' LOOP
    EXECUTE format('ALTER TABLE momna.canonical_field_values DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;
ALTER TABLE momna.canonical_field_values ADD CONSTRAINT canonical_field_values_knowledge_payload_check CHECK (
    (knowledge_state='KNOWN' AND ((typed_value IS NOT NULL) <> (reference_id IS NOT NULL))) OR
    (knowledge_state IN ('UNKNOWN','NOT_ASKED','DECLINED') AND typed_value IS NULL AND reference_id IS NULL AND reference_provider IS NULL)
);
ALTER TABLE momna.canonical_field_values ADD CONSTRAINT canonical_field_values_knowledge_state_check CHECK (knowledge_state IN ('UNKNOWN','NOT_ASKED','DECLINED','KNOWN'));
ALTER TABLE momna.canonical_field_values ADD CONSTRAINT canonical_field_values_definition_version_fk FOREIGN KEY(field_id,definition_version) REFERENCES momna.canonical_field_definition_versions(field_id,definition_version);
CREATE INDEX IF NOT EXISTS idx_canonical_field_definition_versions_namespace ON momna.canonical_field_definition_versions(namespace,field_id,definition_version DESC);
CREATE INDEX IF NOT EXISTS idx_canonical_field_values_definition_version ON momna.canonical_field_values(field_id,definition_version,schema_version);
