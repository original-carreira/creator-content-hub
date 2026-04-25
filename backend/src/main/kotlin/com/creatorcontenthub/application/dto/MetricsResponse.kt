package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class MetricsResponse(
    val totalRequests: Long,
    val routes: Map<String, Long>,
    val python: PythonMetricsResponse,
    val ingestion: IngestionMetricsResponse,
    val ingestionWindow: IngestionMetricsSnapshot,
    val hikari: HikariMetricsResponse
)

@Serializable
data class PythonMetricsResponse(
    val calls: Long,
    val errors: Long,
    val timeouts: Long
)

@Serializable
data class HikariMetricsResponse(
    val active: Int,
    val idle: Int,
    val waiting: Int
)