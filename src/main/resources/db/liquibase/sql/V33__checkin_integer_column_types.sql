-- Match PostgreSQL numeric types to Java int mappings used by JPA.
ALTER TABLE momna.checkin_adjustment_signals
    ALTER COLUMN state_points TYPE INTEGER
    USING state_points::INTEGER;

ALTER TABLE momna.checkin_answers
    ALTER COLUMN value TYPE INTEGER
    USING value::INTEGER;
