-- Keep SHA-256 identifiers variable-width at the SQL type level.
-- The CHECK constraints enforce the exact 64 lowercase hexadecimal characters;
-- VARCHAR(64) matches the JPA String mapping and avoids CHAR padding/type validation drift.
ALTER TABLE momna.checkin_adjustment_signals
    ALTER COLUMN signal_id TYPE VARCHAR(64)
    USING btrim(signal_id)::VARCHAR(64);

ALTER TABLE momna.checkin_idempotency
    ALTER COLUMN fingerprint_sha256 TYPE VARCHAR(64)
    USING btrim(fingerprint_sha256)::VARCHAR(64);
