package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class IngestYoutubeResponse(
    val jobId: String,
    val status: String
)