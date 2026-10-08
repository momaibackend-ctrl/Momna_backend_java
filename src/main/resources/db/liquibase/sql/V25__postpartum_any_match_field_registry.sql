-- CORE-BE-22 (Qira MOMNA-1062): additive Core delta discovered during E17 full onboarding
-- integration. Registers the two canonical Postpartum questions that were left uncompiled in
-- V19 because their legacy visibility (`by_child_any_in`) had no equivalent condition predicate,
-- and adds the per-child *value* vocabulary that AnyMatch's schema validation checks predicates
-- against. V19 itself is left untouched (additive only): this row is evolved with an UPDATE, not
-- a re-insert, since ON CONFLICT DO NOTHING cannot change an existing row.
UPDATE momna.canonical_field_definitions
SET validation_schema = '{"allowedValues": ["home", "hospital", "both", "not_with_me", "deceased", "prefer_not_to_say"]}'::jsonb
WHERE field_id = 'onboarding.postpartum.child_status_by_child';

INSERT INTO momna.canonical_field_definitions(field_id, data_type, domain_owner, sensitivity_class, validation_schema, definition_version)
VALUES
    ('onboarding.postpartum.mixed_loss_support', 'OBJECT', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedKeys": ["remember_baby", "hard_reminders", "care_support", "grief_support", "no_one_decide", "dont_know_yet", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.hospital_access', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["as_long_as_needed", "daily_not_long", "less_than_wanted", "cannot_yet", "prefer_not_to_say"]}'::jsonb, 1)
ON CONFLICT (field_id) DO NOTHING;
