package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetMediaNavigationContextResponse(
    val contextId: String,
    val assetId: String,
    val name: String,
    val createdAt: Long,
    val versionId: String,
    val versionNumber: Int,
    val versionCreatedAt: Long,
    val ranges: List<MediaRangeResponse>
)
