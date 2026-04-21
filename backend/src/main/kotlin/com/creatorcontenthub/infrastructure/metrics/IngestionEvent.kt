package com.creatorcontenthub.infrastructure.metrics

data class IngestionEvent(
    val timestamp: Long,
    val success: Boolean,
    val processingTimeMs: Long
)
