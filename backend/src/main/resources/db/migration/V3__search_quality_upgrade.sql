-- ============================================
-- SEARCH QUALITY UPGRADE (PORTUGUESE + WEIGHTS)
-- ============================================

-- 1. REINDEXACAO DO SEARCH VECTOR
UPDATE jobs
SET search_vector =
        setweight(to_tsvector('portuguese', coalesce(summary, '')), 'A') ||
        setweight(to_tsvector('portuguese', coalesce(transcription, '')), 'B');


-- 2. REMOVER TRIGGER ANTIGO (SE EXISTIR)
DROP TRIGGER IF EXISTS jobs_search_vector_trigger ON jobs;
DROP FUNCTION IF EXISTS jobs_search_vector_update();


-- 3. NOVA FUNCAO COM PESO + PORTUGUESE
CREATE FUNCTION jobs_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('portuguese', coalesce(NEW.summary, '')), 'A') ||
    setweight(to_tsvector('portuguese', coalesce(NEW.transcription, '')), 'B');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- 4. NOVO TRIGGER
CREATE TRIGGER jobs_search_vector_trigger
    BEFORE INSERT OR UPDATE ON jobs
                         FOR EACH ROW EXECUTE FUNCTION jobs_search_vector_update();


-- 5. RECRIAR INDICE GIN (IMPORTANTE)
DROP INDEX IF EXISTS idx_jobs_search_vector;

CREATE INDEX idx_jobs_search_vector
    ON jobs USING GIN (search_vector);