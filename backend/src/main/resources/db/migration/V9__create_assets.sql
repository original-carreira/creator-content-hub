CREATE TABLE assets (

                        asset_id VARCHAR(255) PRIMARY KEY,

                        job_id VARCHAR(255) NOT NULL,

                        asset_type VARCHAR(50) NOT NULL,

                        storage_path TEXT NOT NULL,

                        created_at BIGINT NOT NULL
);

CREATE INDEX idx_assets_job_id
    ON assets(job_id);

CREATE UNIQUE INDEX idx_assets_storage_path
    ON assets(storage_path);