package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class MediaRangeResponse(
    val startTimeMs: Long,
    val endTimeMs: Long
)
