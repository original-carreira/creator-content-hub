package com.creatorcontenthub.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptSegment(
    val start: Double,
    val end: Double,
    val text: String
)
