CREATE TABLE media_navigation_context_versions (
                                                   version_id VARCHAR(255) PRIMARY KEY,
                                                   context_id VARCHAR(255) NOT NULL,
                                                   version_number INTEGER NOT NULL,
                                                   created_at BIGINT NOT NULL,
                                                   ranges_json TEXT NOT NULL
);

CREATE INDEX idx_media_navigation_context_versions_context_id
    ON media_navigation_context_versions(context_id);

CREATE UNIQUE INDEX idx_media_navigation_context_versions_context_version
    ON media_navigation_context_versions(
                                         context_id,
                                         version_number
        );