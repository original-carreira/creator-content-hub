package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchDebugInfo(
    val rank: Double,
    val recencyScore: Double,
    val finalScore: Double
)
