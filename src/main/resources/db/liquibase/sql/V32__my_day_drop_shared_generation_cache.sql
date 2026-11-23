-- A generated My Day may carry personal context, so it must never be served to another user.
-- The shared generation cache is removed until anonymous day templates exist (CORE-BE-13 plan);
-- until then a failed generation answers 503 "not ready yet". No user's My Day is affected.
DROP TABLE momna.my_day_generation_cache;
