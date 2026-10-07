-- E17-DEFINITIONS: canonical field registrations for the Lifecycle Router.
-- Sourced from the verified legacy Router v8 (reference-repos/momna-onboarding-legacy,
-- 20260726102200_router_v8_country_restored.sql). Machine field IDs/options are preserved
-- verbatim from that final state; only storage and validation now go through the Canonical
-- Field Registry instead of bespoke onboarding tables. No legacy RPC/table DDL is copied here.
INSERT INTO momna.canonical_field_definitions(field_id, data_type, domain_owner, sensitivity_class, validation_schema, definition_version)
VALUES
    ('profile.display_name', 'TEXT', 'IDENTITY_PROFILE', 'GENERAL_PROFILE', '{"maxLength":60}'::jsonb, 1),
    ('profile.country_region', 'TEXT', 'IDENTITY_PROFILE', 'GENERAL_PROFILE', '{"maxLength":2,"pattern":"^[A-Z]{2}$"}'::jsonb, 1),
    ('onboarding.router.menarche_status', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["not_started","very_recent","within_two_years","more_than_two_years","unsure","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.current_situation', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["pregnant","possible_pregnancy","recent_birth","planning","cycle_tracking","cycle_changing","no_periods_12m","unsure","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.pregnancy_status', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE',
        '{"allowedValues":["confirmed_by_doctor","test_positive","only_possible","not_pregnant","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.postpartum_time_range', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["lt_1m","m1_3","m4_6","m7_9","m10_12","more_than_year","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.postpartum_main_need', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["recovery","cycle","planning","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.planning_pregnancy_status', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE',
        '{"allowedValues":["no","confirmed","maybe","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.cycle_change_known_cause', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["pregnancy","recent_birth_or_breastfeeding","hormonal","surgery_or_treatment","stress_weight_illness","doctor_said_perimenopause","no_known_cause","do_not_know","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.amenorrhea_known_cause', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["pregnancy","recent_birth_or_breastfeeding","hormonal","ovaries_surgically_removed","hysterectomy_or_other_treatment","doctor_confirmed_menopause","no_known_cause","do_not_know","prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.router.unsure_pregnancy', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE',
        '{"allowedValues":["confirmed","possible","no"]}'::jsonb, 1),
    ('onboarding.router.unsure_recent_birth', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["yes","no","not_sure"]}'::jsonb, 1),
    ('onboarding.router.unsure_planning', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["yes","no","not_sure"]}'::jsonb, 1),
    ('onboarding.router.unsure_cycle_changing', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["yes","no","not_sure"]}'::jsonb, 1),
    ('onboarding.router.unsure_no_periods', 'ENUM', 'ONBOARDING', 'HER_PRIVATE',
        '{"allowedValues":["yes","no","not_sure"]}'::jsonb, 1)
ON CONFLICT (field_id) DO NOTHING;
