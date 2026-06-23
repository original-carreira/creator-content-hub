package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateMediaNavigationContextRequest(
    val ranges: List<MediaRangeRequest>
)
