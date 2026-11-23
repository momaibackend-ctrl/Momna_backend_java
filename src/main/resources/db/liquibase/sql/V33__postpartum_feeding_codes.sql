-- MOMNA-561 / MOMNA-1324, Product decision 24.11.2026: PST_FEEDING ("How is each baby currently being
-- fed?") has seven canonical codes, answered per baby. The clarification "Is the baby currently receiving
-- breast milk, including pumped?" is asked per baby only after tube/medically supported feeding or a
-- changing feeding plan; a skipped answer is UNKNOWN.
-- Additive and versioned (V27 registry): feeding gets definition_version 2, version 1 stays in history.

INSERT INTO momna.canonical_field_definition_versions(field_id, definition_version, namespace, data_type, domain_owner,
    sensitivity_class, validation_schema, schema_version, registry_policy, deprecated_at, created_at, updated_at)
SELECT field_id, 2, namespace, data_type, domain_owner, sensitivity_class,
    '{"allowedValues": ["nursing_at_breast", "pumped_breast_milk", "combination_feeding", "infant_formula", "tube_or_medically_supported", "feeding_plan_changing", "prefer_not_to_say"], "perChild": true}'::jsonb,
    2, registry_policy, NULL, now(), now()
FROM momna.canonical_field_definitions
WHERE field_id = 'onboarding.postpartum.feeding'
ON CONFLICT (field_id, definition_version) DO NOTHING;

UPDATE momna.canonical_field_definitions
SET definition_version = 2,
    schema_version = 2,
    validation_schema = '{"allowedValues": ["nursing_at_breast", "pumped_breast_milk", "combination_feeding", "infant_formula", "tube_or_medically_supported", "feeding_plan_changing", "prefer_not_to_say"], "perChild": true}'::jsonb,
    updated_at = now()
WHERE field_id = 'onboarding.postpartum.feeding' AND definition_version = 1;

INSERT INTO momna.canonical_field_definitions(field_id, namespace, data_type, domain_owner, sensitivity_class,
    validation_schema, definition_version, schema_version)
VALUES ('onboarding.postpartum.feeding_breast_milk_now', 'onboarding', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE',
    '{"allowedValues": ["yes", "no"], "perChild": true}'::jsonb, 1, 1)
ON CONFLICT (field_id) DO NOTHING;

INSERT INTO momna.canonical_field_definition_versions(field_id, definition_version, namespace, data_type, domain_owner,
    sensitivity_class, validation_schema, schema_version, registry_policy, deprecated_at, created_at, updated_at)
SELECT field_id, definition_version, namespace, data_type, domain_owner, sensitivity_class, validation_schema,
    schema_version, registry_policy, deprecated_at, created_at, updated_at
FROM momna.canonical_field_definitions
WHERE field_id = 'onboarding.postpartum.feeding_breast_milk_now'
ON CONFLICT (field_id, definition_version) DO NOTHING;
