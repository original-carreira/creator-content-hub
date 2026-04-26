ALTER TABLE jobs
    ADD COLUMN search_vector tsvector;

UPDATE jobs
SET search_vector =
        to_tsvector('simple',
                    coalesce(summary, '') || ' ' || coalesce(transcription, '')
        );

CREATE FUNCTION jobs_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    to_tsvector('simple',
      coalesce(NEW.summary, '') || ' ' || coalesce(NEW.transcription, '')
    );
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER jobs_search_vector_trigger
    BEFORE INSERT OR UPDATE ON jobs
                         FOR EACH ROW EXECUTE FUNCTION jobs_search_vector_update();

CREATE INDEX idx_jobs_search_vector
    ON jobs USING GIN (search_vector);