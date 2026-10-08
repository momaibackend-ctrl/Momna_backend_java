-- E17-DEFINITIONS-PERIODS: canonical field registrations for the POSTPARTUM period onboarding.
-- Sourced from the final verified legacy state (reference-repos/momna-onboarding-legacy,
-- 20260726102100_postpartum_time_anchor_fix.sql -- schema row version 2, after the time-anchor
-- correction). `child_reference`/`child_status_by_child`/`delivery_by_child`/`feeding` are
-- per-child fields with no fixed key set (child count is itself a dynamic answer), so they are
-- registered as open OBJECT fields with no key/value constraint -- see PostpartumFlowDefinition.
INSERT INTO momna.canonical_field_definitions(field_id, data_type, domain_owner, sensitivity_class, validation_schema, definition_version)
VALUES
    ('onboarding.postpartum.birth_date', 'DATE', 'ONBOARDING', 'HER_PRIVATE', '{"maxDaysFromNow": 0, "minDaysFromNow": -395}'::jsonb, 1),
    ('onboarding.postpartum.time_since_birth_approx', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["less_than_month", "month_1", "month_2", "month_3", "month_4", "month_5", "month_6", "month_7", "month_8", "month_9", "month_10", "month_11", "more_than_year", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.multiples', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["one_baby", "twins", "triplets", "more_than_three", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.child_status', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["all_home", "some_hospital", "split_time", "not_with_me", "loss", "complicated", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.child_count_exact', 'NUMBER', 'ONBOARDING', 'HER_PRIVATE', '{"minimum": 4, "maximum": 8}'::jsonb, 1),
    ('onboarding.postpartum.child_reference', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{}'::jsonb, 1),
    ('onboarding.postpartum.child_status_by_child', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{}'::jsonb, 1),
    ('onboarding.postpartum.gestation', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["full_term", "little_early", "very_early", "after_due", "dont_know", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.delivery', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["vaginal", "planned_c_section", "emergency_c_section", "multiples_all_vaginal", "multiples_all_c_section", "multiples_mixed", "other_combination", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.delivery_by_child', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{}'::jsonb, 1),
    ('onboarding.postpartum.complications', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["heavy_blood_loss", "high_bp_preeclampsia", "infection", "severe_tear", "additional_surgery", "long_hospital_stay", "movement_limits", "another_complication", "none", "not_explained", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.current_care', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["monitor_bp", "monitor_bloodwork", "incision_care", "movement_limits", "hospital_visits", "prescribed_meds", "none", "still_in_hospital", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.physical', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["pain_after_birth", "incision", "bleeding", "weakness_dizziness", "back_pelvic_joint_pain", "urinary_leakage", "pelvic_pressure", "constipation", "swelling", "headaches", "nothing_much", "something_else"]}'::jsonb, 1),
    ('onboarding.postpartum.feeding', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{}'::jsonb, 1),
    ('onboarding.postpartum.feeding_issues', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["sore_nipples", "engorgement", "clogged_duct", "milk_supply_worry", "pumping_effort", "latching_trouble", "differs_per_baby", "support_without_pressure", "none", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.medications', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["pain_meds", "iron", "vitamins", "bp_meds", "antibiotic_treatment", "mental_health_meds", "nothing", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.followup', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["already_had_checkup", "scheduled", "more_frequent", "not_scheduled_yet", "not_sure_when", "still_in_hospital", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.sleep', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["short_stretches", "1_2_hours", "3_4_hours", "becoming_predictable", "cannot_fall_asleep", "depends_on_hospital", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.night_help', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["shared_regularly", "sometimes", "hard_to_ask", "mostly_me", "not_home", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.other_children', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["no", "one_other_child", "more_than_one", "adult_family_member", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.daily_load', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["feeding", "night_wakings", "physical_recovery", "hospital_travel", "multiple_babies", "housework", "older_children_family", "not_enough_help", "anxiety", "grief", "something_else"]}'::jsonb, 1),
    ('onboarding.postpartum.work', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["on_leave", "need_return_soon", "from_home", "in_person", "shifts", "not_working", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.mental', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["mostly_coping", "exhausted_overwhelmed", "anxious", "cry_sad", "irritable_angry", "disconnected", "emotions_change_fast", "guilt_not_enough", "grief", "hard_to_tell", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.functioning', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["mostly_managing", "managing_at_limit", "need_more_help", "sometimes_unable", "recovering_without_baby", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.bonding', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["close", "growing", "mixed", "not_much_yet", "hard_due_to_exhaustion", "hospital_separation", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.safety', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedValues": ["no", "yes_getting_help", "yes_not_getting_help", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.support_network', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["partner", "family_member", "friend", "clinician", "therapist_psychiatrist", "support_group", "no_one", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.partner', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["live_together", "not_living_together", "no_partner", "uncertain_complicated", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.partner_load', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedValues": ["equal", "mostly_me", "rarely_involved", "unable_physically", "hard_to_ask", "tension", "pressured_unsafe", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.intimacy_status', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedValues": ["yes_regularly", "sometimes", "not_yet_comfortable", "not_yet_worried", "medical_restriction", "not_applicable", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.intimacy_comfort', 'OBJECT', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedKeys": ["comfortable", "desire_decreased", "dryness_pain", "fear_pregnancy", "body_discomfort", "partner_pressure", "want_affection_no_sex", "hard_to_talk", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.next_pregnancy', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["yes_important", "already_chosen", "havent_thought", "maybe_later", "not_applicable", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.postpartum.rest', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["sleep", "shower_selfcare", "short_walk", "music_podcast", "reading_watching", "talking_trusted", "time_alone", "leaving_house", "nothing_helps", "something_else"]}'::jsonb, 1),
    ('onboarding.postpartum.behavior', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"requiredKeys": ["steps", "planning", "depth", "reminders", "checkins"], "allowedKeys": ["steps", "planning", "depth", "reminders", "checkins"]}'::jsonb, 1),
    ('onboarding.postpartum.tone', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["gentle", "friendly", "close_friend", "direct"]}'::jsonb, 1)
ON CONFLICT (field_id) DO NOTHING;
