-- Design 270-8891 (Yulia, 26.11.2026) replaces V39: without a birth date, the postpartum onboarding asks
-- "How long has it been since the birth?" on a wheel of months (less than a month, 1-11 months), and the
-- answer may be skipped. The router asks no time range (router v2), so "more than a year" no longer sends
-- the user back. Additive and versioned (V27 registry): the field gets the next definition_version.
CREATE TEMPORARY TABLE registry_additions(field_id TEXT PRIMARY KEY, addition JSONB NOT NULL) ON COMMIT DROP;
INSERT INTO registry_additions VALUES
('onboarding.postpartum.time_since_birth_approx',
 '{"allowedValues": ["less_than_month", "month_1", "month_2", "month_3", "month_4", "month_5", "month_6", "month_7", "month_8", "month_9", "month_10", "month_11"]}');

INSERT INTO momna.canonical_field_definition_versions(field_id, definition_version, namespace, data_type, domain_owner,
    sensitivity_class, validation_schema, schema_version, registry_policy, deprecated_at, created_at, updated_at)
SELECT d.field_id, d.definition_version + 1, d.namespace, d.data_type, d.domain_owner, d.sensitivity_class,
    (d.validation_schema - 'changeBeforeComplete') || a.addition, d.schema_version + 1, d.registry_policy, NULL, now(), now()
FROM momna.canonical_field_definitions d JOIN registry_additions a ON a.field_id = d.field_id
ON CONFLICT (field_id, definition_version) DO NOTHING;

UPDATE momna.canonical_field_definitions d
SET definition_version = d.definition_version + 1, schema_version = d.schema_version + 1,
    validation_schema = (d.validation_schema - 'changeBeforeComplete') || a.addition, updated_at = now()
FROM registry_additions a
WHERE a.field_id = d.field_id;
