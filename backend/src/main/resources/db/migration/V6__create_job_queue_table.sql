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
                           last_heartbeat_at BIGINT NULL,
                           retry_at BIGINT NULL
);

CREATE TABLE dead_letter_queue (
                                   id BIGSERIAL PRIMARY KEY,
                                   job_id VARCHAR(255) NOT NULL,
                                   queue_id BIGINT NULL,
                                   stage VARCHAR(255) NULL,
                                   error_message TEXT NULL,
                                   failed_at BIGINT NOT NULL,
                                   attempts INTEGER NOT NULL,
                                   worker_id VARCHAR(255) NULL,
                                   payload_snapshot TEXT NULL
);

CREATE INDEX idx_job_queue_pending_claim
    ON job_queue(created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_job_queue_job_id
    ON job_queue(job_id);

CREATE INDEX idx_job_queue_processing_heartbeat
    ON job_queue(last_heartbeat_at)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_job_queue_retry_ready
    ON job_queue(retry_at)
    WHERE retry_at IS NOT NULL;

CREATE INDEX idx_dlq_job_id
    ON dead_letter_queue(job_id);

CREATE INDEX idx_dlq_failed_at
    ON dead_letter_queue(failed_at);