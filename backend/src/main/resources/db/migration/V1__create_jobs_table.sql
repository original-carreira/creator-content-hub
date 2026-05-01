-- ============================================
-- CREATE JOBS TABLE (BASE MIGRATION)
-- ============================================

CREATE TABLE jobs (
                      job_id TEXT PRIMARY KEY,
                      status TEXT NOT NULL,

                      created_at BIGINT NOT NULL,
                      started_at BIGINT NOT NULL,
                      finished_at BIGINT,

                      transcription TEXT,
                      transcription_completed_at BIGINT,

                      summary TEXT,
                      summary_completed_at BIGINT,

                      error_type TEXT,
                      error_message TEXT
);