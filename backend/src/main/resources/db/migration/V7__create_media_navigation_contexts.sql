CREATE TABLE media_navigation_contexts (
                                           context_id VARCHAR(255) PRIMARY KEY,
                                           asset_id VARCHAR(255) NOT NULL,
                                           name VARCHAR(255) NOT NULL,
                                           created_at BIGINT NOT NULL,
                                           active_version_id VARCHAR(255) NOT NULL
);

CREATE INDEX idx_media_navigation_contexts_asset_id
    ON media_navigation_contexts(asset_id);

CREATE INDEX idx_media_navigation_contexts_active_version_id
    ON media_navigation_contexts(active_version_id);