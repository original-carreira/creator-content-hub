package com.creatorcontenthub.application.dto

data class IngestionWindowMetricsSnapshot(
    val windowSizeSeconds: Long,
    val started: Long,
    val succeeded: Long,
    val failed: Long,
    val successRate: Double,
    val failureRate: Double,
    val avgProcessingTimeMs: Long
)
