package com.creatorcontenthub.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaRange(
    val startTimeMs: Long,
    val endTimeMs: Long
) {

    init {

        require(startTimeMs >= 0) {
            "startTimeMs must be >= 0"
        }

        require(endTimeMs >= 0) {
            "endTimeMs must be >= 0"
        }

        require(startTimeMs < endTimeMs) {
            "startTimeMs must be < endTimeMs"
        }
    }
}
