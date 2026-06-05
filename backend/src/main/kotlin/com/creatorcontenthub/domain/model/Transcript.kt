package com.creatorcontenthub.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Transcript(
    val text: String,
    val segments: List<TranscriptSegment>
)
