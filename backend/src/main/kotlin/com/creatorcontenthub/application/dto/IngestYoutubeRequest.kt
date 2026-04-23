package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class IngestYoutubeRequest(
    val url: String
)