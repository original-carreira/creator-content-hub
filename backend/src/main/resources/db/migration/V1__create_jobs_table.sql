-- ============================================
-- CREATE JOBS TABLE (BASE MIGRATION)
-- ============================================

CREATE TABLE jobs (
                      job_id TEXT PRIMARY KEY,
                      status TEXT NOT NULL,
                      created_at BIGINT NOT NULL,
                      finished_at BIGINT,
                      transcription TEXT,
                      summary TEXT
);