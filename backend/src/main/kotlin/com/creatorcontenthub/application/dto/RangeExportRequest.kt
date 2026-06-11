package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class RangeSelectionRequest(
    val startIndex: Int,
    val endIndex: Int
)

@Serializable
data class RangeExportRequest(
    val ranges: List<RangeSelectionRequest>
)
