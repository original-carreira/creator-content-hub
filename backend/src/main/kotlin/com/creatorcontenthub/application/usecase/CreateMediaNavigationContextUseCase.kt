package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.CreateMediaNavigationContextRequest
import com.creatorcontenthub.application.dto.CreateMediaNavigationContextResponse
import com.creatorcontenthub.application.port.MediaNavigationContextRepository
import com.creatorcontenthub.domain.model.MediaNavigationContext
import com.creatorcontenthub.domain.model.MediaNavigationContextVersion
import com.creatorcontenthub.domain.model.MediaRange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class CreateMediaNavigationContextUseCase(
    private val repository: MediaNavigationContextRepository
) {

    fun execute(
        request: CreateMediaNavigationContextRequest
    ): CreateMediaNavigationContextResponse {

        require(request.assetId.isNotBlank()) {
            "assetId must not be blank"
        }

        require(request.ranges.isNotEmpty()) {
            "ranges must not be empty"
        }

        val now = System.currentTimeMillis()

        val contextId =
            UUID.randomUUID().toString()

        val versionId =
            UUID.randomUUID().toString()

        val mediaRanges =
            request.ranges.map {
                MediaRange(
                    startTimeMs = it.startTimeMs,
                    endTimeMs = it.endTimeMs
                )
            }

        val version =
            MediaNavigationContextVersion(
                versionId = versionId,
                contextId = contextId,
                versionNumber = 1,
                createdAt = now,
                mediaRanges = mediaRanges
            )

        val context =
            MediaNavigationContext(
                contextId = contextId,
                assetId = request.assetId,
                name = generateDefaultName(now),
                createdAt = now,
                activeVersion = version
            )

        repository.create(
            contextId = contextId,
            context = context
        )

        return CreateMediaNavigationContextResponse(
            contextId = contextId
        )
    }

    private fun generateDefaultName(
        timestamp: Long
    ): String {

        val formatter =
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm"
            )

        val formatted =
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(formatter)

        return "Seleção $formatted"
    }
}