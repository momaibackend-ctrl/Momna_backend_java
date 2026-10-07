-- E17-DEFINITIONS-PERIODS: canonical field registrations for the MENARCHE period onboarding.
-- Sourced from the final verified legacy state (reference-repos/momna-onboarding-legacy,
-- 20260726101300_required_questions.sql -- schema row version 2, the finalized required/optional
-- split for this period).
INSERT INTO momna.canonical_field_definitions(field_id, data_type, domain_owner, sensitivity_class, validation_schema, definition_version)
VALUES
    ('onboarding.menarche.goal', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["understand_body", "get_ready", "less_worried", "predict_next", "handle_cramps", "learn_products", "understand_mood", "know_when_help", "explore_own_pace"]}'::jsonb, 1),
    ('onboarding.menarche.body_feel', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["calm_curious", "unfamiliar_ok", "embarrassed", "compare_others", "worried_wrong", "not_noticed", "not_sure", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.changes', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["breasts", "body_hair", "discharge", "acne", "height_shape", "mood_changes", "body_odor", "none_yet", "not_sure", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.first_period_worry', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["at_school", "might_hurt", "lot_of_blood", "wont_know_what_to_do", "someone_notice", "awkward_adult", "dont_know_products", "not_worried", "something_else", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.first_experience', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["easier_than_expected", "unfamiliar_handled", "started_unexpectedly", "painful", "scared_me", "awkward_asking_help", "not_sure_how_feel", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.last_period', 'DATE', 'ONBOARDING', 'HER_PRIVATE', '{"maxDaysFromNow": 0, "minDaysFromNow": -60}'::jsonb, 1),
    ('onboarding.menarche.period_pattern', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["only_one", "lasts_1_2", "lasts_3_5", "lasts_6_7", "longer_than_week", "bleed_through", "change_often", "different_every_time", "not_sure_yet", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.symptoms', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["cramps", "back_pain", "headaches", "fatigue", "nausea", "bloating", "mood_swings", "anxiety_crying", "acne", "appetite_changes", "nothing_noticeable", "not_enough_periods", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.impact', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["no_impact", "harder_sometimes", "miss_activity", "barely_function", "dont_know_yet", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.products', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["pads", "tampons", "period_underwear", "menstrual_cups", "none_yet", "want_to_understand", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.day_context', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["school", "college", "work", "mostly_home", "varies", "somewhere_else", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.toilet_access', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["usually_easy", "need_permission", "sometimes_difficult", "almost_impossible", "dont_know_yet", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.notifications', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["direct_ok", "neutral_wording", "only_important", "no_notifications"]}'::jsonb, 1),
    ('onboarding.menarche.trusted_adult', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["easy_to_talk", "a_bit_awkward", "someone_unsure", "no_one", "dont_want_to", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.health_talk', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["ask_directly", "read_first", "need_a_sentence", "very_uncomfortable", "not_sure", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.health_context', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["anemia", "bleeding_condition", "thyroid", "heavy_painful_periods", "other_condition", "no", "dont_know", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.sleep', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["enough_sleep", "stay_up_late", "trouble_falling_asleep", "wake_often", "hard_to_get_up", "different_every_day", "not_sure", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.activity', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["move_a_lot", "mixed", "sit_most_day", "physically_exhausted", "varies", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.emotions', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["calm", "happy_energetic", "tired", "irritable", "anxious", "sad", "lonely", "mood_changed_a_lot", "hard_to_tell", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.body_image', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["mostly_comfortable", "changes_day_to_day", "often_unhappy", "hide_changes", "hard_not_to_criticize", "dont_think_about_it", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.free_time', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"allowedKeys": ["music", "walking", "sports_dancing", "reading", "games", "drawing_making", "friends", "time_alone", "movies_shows", "not_sure_yet", "something_else"]}'::jsonb, 1),
    ('onboarding.menarche.relationship', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["yes", "starting", "no", "not_sure_how_to_describe", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.relationship_safety', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedValues": ["comfortable_safe", "mostly_good", "hard_to_say_no", "pressured", "afraid_unsafe", "not_sure", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.sex_active', 'ENUM', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedValues": ["yes", "sometimes", "no", "not_sure_what_counts", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.sex_comfort', 'OBJECT', 'ONBOARDING', 'MEDICAL_PRIVATE', '{"allowedKeys": ["preventing_pregnancy", "preventing_sti", "pain_discomfort", "consent_boundaries", "talk_to_partner", "everything_ok", "not_sure", "prefer_not_to_say"]}'::jsonb, 1),
    ('onboarding.menarche.behavior', 'OBJECT', 'ONBOARDING', 'HER_PRIVATE', '{"requiredKeys": ["planning", "depth", "reminders", "steps"], "allowedKeys": ["planning", "depth", "reminders", "steps"]}'::jsonb, 1),
    ('onboarding.menarche.tone', 'ENUM', 'ONBOARDING', 'HER_PRIVATE', '{"allowedValues": ["gentle", "friendly", "close_friend", "direct"]}'::jsonb, 1)
ON CONFLICT (field_id) DO NOTHING;
