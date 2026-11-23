-- MOMNA-561 / MOMNA-1324, Product decision 24.11.2026: PST_FEEDING ("How is each baby currently being
-- fed?") has seven canonical codes, answered per baby. Additive: the V19 row is evolved with an UPDATE.
-- The clarification "Is the baby currently receiving breast milk, including pumped?" is asked per baby
-- only after tube/medically supported feeding or a changing feeding plan; a skipped answer is UNKNOWN.
UPDATE momna.canonical_field_definitions
SET validation_schema = '{"allowedValues": ["nursing_at_breast", "pumped_breast_milk", "combination_feeding", "infant_formula", "tube_or_medically_supported", "feeding_plan_changing", "prefer_not_to_say"], "perChild": true}'::jsonb
WHERE field_id = 'onboarding.postpartum.feeding';

INSERT INTO momna.canonical_field_definitions(field_id, data_type, domain_owner, sensitivity_class, validation_schema, definition_version)
VALUES
    ('onboarding.postpartum.feeding_breast_milk_now', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["yes", "no"], "perChild": true}'::jsonb, 1)
ON CONFLICT (field_id) DO NOTHING;
