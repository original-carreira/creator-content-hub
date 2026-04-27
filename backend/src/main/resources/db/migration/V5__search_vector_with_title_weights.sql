-- ============================================
-- SEARCH VECTOR WITH TITLE PRIORITY
-- ============================================
SET search_path TO public;
-- 1. REPROCESSAR DADOS EXISTENTES
UPDATE jobs
SET search_vector =
        setweight(to_tsvector('portuguese', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('portuguese', coalesce(summary, '')), 'B') ||
        setweight(to_tsvector('portuguese', coalesce(transcription, '')), 'C');


-- 2. ATUALIZAR FUNCAO DO TRIGGER
CREATE OR REPLACE FUNCTION jobs_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('portuguese', coalesce(NEW.title, '')), 'A') ||
    setweight(to_tsvector('portuguese', coalesce(NEW.summary, '')), 'B') ||
    setweight(to_tsvector('portuguese', coalesce(NEW.transcription, '')), 'C');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- 3. (OPCIONAL) REINDEXAR PARA GARANTIA
REINDEX INDEX idx_jobs_search_vector;