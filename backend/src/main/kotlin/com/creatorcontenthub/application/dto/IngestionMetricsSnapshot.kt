package com.creatorcontenthub.application.dto

data class IngestionMetricsSnapshot(
    val started: Long,
    val succeeded: Long,
    val failed: Long,

    val successRate: Double,
    val failureRate: Double,

    val rejected: Long,

    val avgProcessingTimeMs: Long,

    val failedTimeout: Long,
    val failedProcess: Long,
    val failedUnknown: Long
)