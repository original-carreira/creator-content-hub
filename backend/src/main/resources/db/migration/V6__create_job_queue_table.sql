CREATE TABLE job_queue (
                           id BIGSERIAL PRIMARY KEY,
                           job_id VARCHAR(255) NOT NULL,
                           status VARCHAR(32) NOT NULL,
                           created_at BIGINT NOT NULL,
                           started_at BIGINT NULL,
                           completed_at BIGINT NULL,
                           attempts INTEGER NOT NULL DEFAULT 0,
                           error_message TEXT NULL,
                           claimed_by VARCHAR(255) NULL,
                           last_heartbeat_at BIGINT NULL
);

CREATE INDEX idx_job_queue_pending_claim
    ON job_queue(created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_job_queue_job_id
    ON job_queue(job_id);

CREATE INDEX idx_job_queue_processing_heartbeat
    ON job_queue(last_heartbeat_at)
    WHERE status = 'PROCESSING';