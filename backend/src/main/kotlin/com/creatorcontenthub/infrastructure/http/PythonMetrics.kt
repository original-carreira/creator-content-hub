package com.creatorcontenthub.infrastructure.http

data class PythonMetrics(
    val calls: Long,
    val errors: Long,
    val timeouts: Long
)