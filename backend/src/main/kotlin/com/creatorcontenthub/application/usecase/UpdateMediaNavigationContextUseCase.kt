package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.UpdateMediaNavigationContextRequest
import com.creatorcontenthub.application.port.MediaNavigationContextRepository
import com.creatorcontenthub.domain.model.MediaNavigationContextVersion
import com.creatorcontenthub.domain.model.MediaRange
import java.util.UUID

class UpdateMediaNavigationContextUseCase(
    private val repository: MediaNavigationContextRepository
) {

    fun execute(
        contextId: String,
        request: UpdateMediaNavigationContextRequest
    ): Boolean {

        require(contextId.isNotBlank()) {
            "contextId must not be blank"
        }

        require(request.ranges.isNotEmpty()) {
            "ranges must not be empty"
        }

        val existingContext =
            repository.findById(contextId)
                ?: return false

        val mediaRanges =
            request.ranges.map { range ->

                MediaRange(
                    startTimeMs = range.startTimeMs,
                    endTimeMs = range.endTimeMs
                )
            }

        val nextVersion =
            MediaNavigationContextVersion(
                versionId = UUID.randomUUID().toString(),
                contextId = existingContext.contextId,
                versionNumber =
                    existingContext.activeVersion.versionNumber + 1,
                createdAt = System.currentTimeMillis(),
                mediaRanges = mediaRanges
            )

        val updatedContext =
            existingContext.copy(
                activeVersion = nextVersion
            )

        repository.update(
            contextId = contextId,
            context = updatedContext
        )

        return true
    }
}