-- -------------------------------
-- ADD COLUMN (SAFE)
-- -------------------------------
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- -------------------------------
-- BACKFILL (SAFE)
-- -------------------------------
UPDATE jobs
SET search_vector =
        to_tsvector('simple',
                    coalesce(summary, '') || ' ' || coalesce(transcription, '')
        )
WHERE search_vector IS NULL;

-- -------------------------------
-- FUNCTION (IDEMPOTENTE)
-- -------------------------------
CREATE OR REPLACE FUNCTION jobs_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
            to_tsvector('simple',
                        coalesce(NEW.summary, '') || ' ' || coalesce(NEW.transcription, '')
            );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- -------------------------------
-- TRIGGER (SAFE)
-- -------------------------------
DROP TRIGGER IF EXISTS jobs_search_vector_trigger ON jobs;

CREATE TRIGGER jobs_search_vector_trigger
    BEFORE INSERT OR UPDATE ON jobs
    FOR EACH ROW
EXECUTE FUNCTION jobs_search_vector_update();

-- -------------------------------
-- INDEX (SAFE)
-- -------------------------------
CREATE INDEX IF NOT EXISTS idx_jobs_search_vector
    ON jobs USING GIN (search_vector);