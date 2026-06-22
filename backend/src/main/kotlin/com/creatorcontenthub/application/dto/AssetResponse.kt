package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class AssetResponse(
    val assetId: String,
    val assetType: String,
    val createdAt: Long
)
