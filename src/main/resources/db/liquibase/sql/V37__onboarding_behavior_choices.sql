-- ONB-API-01 (MOMNA-1024): "How do you prefer to receive support?" is one choice per group. Each required key
-- of a period's behavior field now lists its two choice codes, in the order of the app's options, so a client
-- knows what to send and the server can check it. Additive and versioned (V27 registry): definition_version 2.
CREATE TEMPORARY TABLE behavior_choices(field_id TEXT PRIMARY KEY, choice_schema JSONB NOT NULL) ON COMMIT DROP;
INSERT INTO behavior_choices VALUES
('onboarding.menarche.behavior', '{"requiredKeys": ["planning", "depth", "reminders", "steps"], "allowedKeys": ["planning", "depth", "reminders", "steps"], "valuesByKey": {"planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "reminders": ["regular", "in_app_only"], "steps": ["one", "few"]}}'),
('onboarding.cycle.behavior', '{"requiredKeys": ["planning", "depth", "length", "reminders", "steps"], "allowedKeys": ["planning", "depth", "length", "reminders", "steps"], "valuesByKey": {"planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "length": ["brief", "detailed"], "reminders": ["regular", "in_app_only"], "steps": ["one", "few"]}}'),
('onboarding.planning.behavior', '{"requiredKeys": ["planning", "depth", "length", "reminders", "steps"], "allowedKeys": ["planning", "depth", "length", "reminders", "steps"], "valuesByKey": {"planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "length": ["brief", "detailed"], "reminders": ["regular", "important_only"], "steps": ["one", "few"]}}'),
('onboarding.pregnancy.behavior', '{"requiredKeys": ["planning", "depth", "length", "reminders", "steps"], "allowedKeys": ["planning", "depth", "length", "reminders", "steps"], "valuesByKey": {"planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "length": ["brief", "detailed"], "reminders": ["regular", "important_only"], "steps": ["one", "few"]}}'),
('onboarding.postpartum.behavior', '{"requiredKeys": ["steps", "planning", "depth", "reminders", "checkins"], "allowedKeys": ["steps", "planning", "depth", "reminders", "checkins"], "valuesByKey": {"steps": ["one", "few"], "planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "reminders": ["regular", "important_only"], "checkins": ["often", "only_when_needed"]}}'),
('onboarding.perimenopause.behavior', '{"requiredKeys": ["planning", "depth", "length", "reminders", "steps"], "allowedKeys": ["planning", "depth", "length", "reminders", "steps"], "valuesByKey": {"planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "length": ["brief", "detailed"], "reminders": ["regular", "important_only"], "steps": ["one", "few"]}}'),
('onboarding.menopause.behavior', '{"requiredKeys": ["planning", "depth", "length", "reminders", "steps"], "allowedKeys": ["planning", "depth", "length", "reminders", "steps"], "valuesByKey": {"planning": ["plan_ahead", "as_i_go"], "depth": ["action_first", "explain_first"], "length": ["brief", "detailed"], "reminders": ["regular", "important_only"], "steps": ["one", "few"]}}');

INSERT INTO momna.canonical_field_definition_versions(field_id, definition_version, namespace, data_type, domain_owner,
    sensitivity_class, validation_schema, schema_version, registry_policy, deprecated_at, created_at, updated_at)
SELECT d.field_id, 2, d.namespace, d.data_type, d.domain_owner, d.sensitivity_class, c.choice_schema, 2, d.registry_policy,
    NULL, now(), now()
FROM momna.canonical_field_definitions d JOIN behavior_choices c ON c.field_id = d.field_id
ON CONFLICT (field_id, definition_version) DO NOTHING;

UPDATE momna.canonical_field_definitions d
SET definition_version = 2, schema_version = 2, validation_schema = c.choice_schema, updated_at = now()
FROM behavior_choices c
WHERE c.field_id = d.field_id AND d.definition_version = 1;
