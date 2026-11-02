package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExportMaterializedSelectionRequest(
    val selectionId: String,
    val startTime: Double,
    val endTime: Double
)
