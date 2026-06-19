package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class MediaRangeRequest(
    val startTimeMs: Long,
    val endTimeMs: Long
)