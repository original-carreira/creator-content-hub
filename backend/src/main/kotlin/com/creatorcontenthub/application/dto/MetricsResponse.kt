package com.creatorcontenthub.application.dto

data class MetricsResponse(
    val totalRequests: Long,
    val routes: Map<String, Long>,
    val python: PythonMetricsResponse
)

data class PythonMetricsResponse(
    val calls: Long,
    val errors: Long,
    val timeouts: Long
)