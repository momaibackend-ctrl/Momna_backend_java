-- MOMNA-561 / MOMNA-1324, design brief 24.11.2026: the clarification "Is your baby currently receiving
-- breast milk?" has four answers: Yes / No / I'm not sure / Prefer not to answer. Only "yes" turns
-- post_breastfeeding on; "not_sure" and "prefer_not_to_say" are UNKNOWN, like a skipped answer.
-- Versioned (V27 registry): definition_version 2, version 1 (yes / no) stays in history.

INSERT INTO momna.canonical_field_definition_versions(field_id, definition_version, namespace, data_type, domain_owner,
    sensitivity_class, validation_schema, schema_version, registry_policy, deprecated_at, created_at, updated_at)
SELECT field_id, 2, namespace, data_type, domain_owner, sensitivity_class,
    '{"allowedValues": ["yes", "no", "not_sure", "prefer_not_to_say"], "perChild": true}'::jsonb,
    2, registry_policy, NULL, now(), now()
FROM momna.canonical_field_definitions
WHERE field_id = 'onboarding.postpartum.feeding_breast_milk_now'
ON CONFLICT (field_id, definition_version) DO NOTHING;

UPDATE momna.canonical_field_definitions
SET definition_version = 2,
    schema_version = 2,
    validation_schema = '{"allowedValues": ["yes", "no", "not_sure", "prefer_not_to_say"], "perChild": true}'::jsonb,
    updated_at = now()
WHERE field_id = 'onboarding.postpartum.feeding_breast_milk_now' AND definition_version = 1;
