package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateMediaNavigationContextRequest(
    val assetId: String,
    val ranges: List<MediaRangeRequest>
)
