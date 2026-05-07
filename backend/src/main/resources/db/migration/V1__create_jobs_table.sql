-- ============================================
-- CREATE JOBS TABLE (BASE MIGRATION)
-- ============================================

CREATE TABLE jobs (
                      job_id TEXT PRIMARY KEY,

                      status TEXT NOT NULL,
                      stage TEXT NOT NULL,

                      created_at BIGINT NOT NULL,
                      started_at BIGINT NOT NULL,
                      finished_at BIGINT,

                      video_id VARCHAR(20),
                      title TEXT,

                      transcription TEXT,
                      transcription_completed_at BIGINT,

                      summary TEXT,
                      summary_completed_at BIGINT,

                      transcription_path TEXT,
                      summary_path TEXT,
                      audio_path TEXT,
                      thumbnail_url TEXT,

                      error_type TEXT,
                      error_message TEXT
);

CREATE INDEX idx_jobs_video_id ON jobs(video_id);

CREATE UNIQUE INDEX idx_jobs_video_id_unique
    ON jobs(video_id)
    WHERE video_id IS NOT NULL;