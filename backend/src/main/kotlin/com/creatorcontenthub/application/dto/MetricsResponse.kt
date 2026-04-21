package com.creatorcontenthub.application.dto

data class MetricsResponse(
    val totalRequests: Long,
    val routes: Map<String, Long>,
    val python: PythonMetricsResponse,
    val ingestion: IngestionMetricsSnapshot,
    val ingestionWindow: IngestionWindowMetricsSnapshot
)

data class PythonMetricsResponse(
    val calls: Long,
    val errors: Long,
    val timeouts: Long
)