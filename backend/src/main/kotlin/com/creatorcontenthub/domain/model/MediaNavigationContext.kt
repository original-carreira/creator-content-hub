package com.creatorcontenthub.domain.model

data class MediaNavigationContext(
    val contextId: String,
    val assetId: String,
    val name: String,
    val createdAt: Long,
    val activeVersion: MediaNavigationContextVersion
) {

    init {

        require(contextId.isNotBlank()) {
            "contextId must not be blank"
        }

        require(assetId.isNotBlank()) {
            "assetId must not be blank"
        }

        require(name.isNotBlank()) {
            "name must not be blank"
        }

        require(
            activeVersion.contextId == contextId
        ) {
            "activeVersion must belong to context"
        }
    }
}
