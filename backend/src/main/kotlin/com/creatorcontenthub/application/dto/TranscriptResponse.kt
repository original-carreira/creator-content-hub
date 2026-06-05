package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptResponse(
    val text: String,
    val segments: List<TranscriptSegmentResponse>
)

@Serializable
data class TranscriptSegmentResponse(
    val start: Double,
    val end: Double,
    val text: String
)
