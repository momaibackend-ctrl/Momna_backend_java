-- MOMNA-1331 item 8, original PST_MONTHS_SINCE_BIRTH_RANGE (MOMNA-448 DOCX), Product decision 26.11.2026:
-- the time since birth, asked when the birth date is declined, is a range as in the router, with "Prefer not to
-- answer". "More than 1 year ago" ends nothing: the first year after birth no longer fits, so the app returns to
-- the router's situation question and the onboarding cannot complete while it stands (changeBeforeComplete).
-- Additive and versioned (V27 registry): the field gets the next definition_version.
CREATE TEMPORARY TABLE registry_additions(field_id TEXT PRIMARY KEY, addition JSONB NOT NULL) ON COMMIT DROP;
INSERT INTO registry_additions VALUES
('onboarding.postpartum.time_since_birth_approx',
 '{"allowedValues": ["lt_1m", "m1_3", "m4_6", "m7_9", "m10_12", "more_than_year", "prefer_not_to_say"], "changeBeforeComplete": ["more_than_year"]}');

INSERT INTO momna.canonical_field_definition_versions(field_id, definition_version, namespace, data_type, domain_owner,
    sensitivity_class, validation_schema, schema_version, registry_policy, deprecated_at, created_at, updated_at)
SELECT d.field_id, d.definition_version + 1, d.namespace, d.data_type, d.domain_owner, d.sensitivity_class,
    d.validation_schema || a.addition, d.schema_version + 1, d.registry_policy, NULL, now(), now()
FROM momna.canonical_field_definitions d JOIN registry_additions a ON a.field_id = d.field_id
ON CONFLICT (field_id, definition_version) DO NOTHING;

UPDATE momna.canonical_field_definitions d
SET definition_version = d.definition_version + 1, schema_version = d.schema_version + 1,
    validation_schema = d.validation_schema || a.addition, updated_at = now()
FROM registry_additions a
WHERE a.field_id = d.field_id;
