package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class IngestionMetricsResponse(
    val ingestionJobsStarted: Long,
    val ingestionJobsSucceeded: Long,
    val ingestionJobsFailed: Long,
    val ingestionProcessingTimeMsTotal: Long,

    val ingestionJobsFailedTimeout: Long,
    val ingestionJobsFailedProcess: Long,
    val ingestionJobsFailedUnknown: Long,

    val ingestionSuccessRate: Double,
    val ingestionFailureRate: Double,
    val ingestionAvgProcessingTimeMs: Double
)