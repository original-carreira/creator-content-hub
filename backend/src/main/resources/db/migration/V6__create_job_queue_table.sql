CREATE TABLE job_queue (
                           id BIGSERIAL PRIMARY KEY,
                           job_id VARCHAR(255) NOT NULL,
                           status VARCHAR(32) NOT NULL,
                           created_at BIGINT NOT NULL,
                           started_at BIGINT NULL,
                           completed_at BIGINT NULL,
                           attempts INTEGER NOT NULL DEFAULT 0,
                           error_message TEXT NULL
);

CREATE INDEX idx_job_queue_status
    ON job_queue(status);

CREATE INDEX idx_job_queue_job_id
    ON job_queue(job_id);