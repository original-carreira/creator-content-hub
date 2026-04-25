package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
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
    val failedUnknown: Long,
    val downloadTimeMsTotal: Long,
    val transcriptionTimeMsTotal: Long,
    val summarizationTimeMsTotal: Long
)