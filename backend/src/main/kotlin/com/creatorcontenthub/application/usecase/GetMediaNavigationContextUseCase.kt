package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.GetMediaNavigationContextResponse
import com.creatorcontenthub.application.dto.MediaRangeResponse
import com.creatorcontenthub.application.port.MediaNavigationContextRepository

class GetMediaNavigationContextUseCase(
    private val repository: MediaNavigationContextRepository
) {

    fun execute(
        contextId: String
    ): GetMediaNavigationContextResponse? {

        val context =
            repository.findById(contextId)
                ?: return null

        return GetMediaNavigationContextResponse(
            contextId = context.contextId,
            assetId = context.assetId,
            name = context.name,
            createdAt = context.createdAt,
            versionId = context.activeVersion.versionId,
            versionNumber = context.activeVersion.versionNumber,
            versionCreatedAt = context.activeVersion.createdAt,
            ranges =
                context.activeVersion.mediaRanges.map {
                    MediaRangeResponse(
                        startTimeMs = it.startTimeMs,
                        endTimeMs = it.endTimeMs
                    )
                }
        )
    }
}