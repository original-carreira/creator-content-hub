package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.MediaNavigationContext

interface MediaNavigationContextRepository {

    fun create(
        contextId: String,
        context: MediaNavigationContext
    )

    fun update(
        contextId: String,
        context: MediaNavigationContext
    )

    fun delete(
        contextId: String
    )

    fun findById(
        contextId: String
    ): MediaNavigationContext?

    fun findByAssetId(
        assetId: String
    ): MediaNavigationContext?

}