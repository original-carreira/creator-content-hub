package com.creatorcontenthub.infrastructure.metrics

import com.creatorcontenthub.application.dto.IngestionMetricsResponse
import com.creatorcontenthub.application.dto.IngestionMetricsSnapshot
import com.creatorcontenthub.domain.model.ErrorType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class IngestionMetrics {

    private val jobsStarted = AtomicLong(0)
    private val jobsSucceeded = AtomicLong(0)
    private val jobsFailed = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)

    private val timeoutFailures = AtomicLong(0)
    private val processFailures = AtomicLong(0)
    private val unknownFailures = AtomicLong(0)

    // 🆕 BACKPRESSURE
    private val ingestionQueueRejections = AtomicLong(0)

    // 🆕 MÉTRICAS POR ESTÁGIO (tempo)
    private val downloadTimeMsTotal = AtomicLong(0)
    private val transcriptionTimeMsTotal = AtomicLong(0)
    private val summarizationTimeMsTotal = AtomicLong(0)

    // 🆕 RESILIÊNCIA (NOVO - NÃO EXPÕE NO DTO)
    private val retriesByStage = ConcurrentHashMap<String, AtomicLong>()
    private val timeoutsByStage = ConcurrentHashMap<String, AtomicLong>()

    // ========================
    // JOB METRICS
    // ========================

    fun incrementStarted() {
        jobsStarted.incrementAndGet()
    }

    fun incrementSucceeded() {
        jobsSucceeded.incrementAndGet()
    }

    fun incrementFailed(errorType: ErrorType) {
        jobsFailed.incrementAndGet()

        when (errorType) {
            ErrorType.TIMEOUT -> timeoutFailures.incrementAndGet()
            ErrorType.PROCESS_ERROR -> processFailures.incrementAndGet()
            ErrorType.DEPENDENCY_FAILURE -> unknownFailures.incrementAndGet()
            ErrorType.UNKNOWN -> unknownFailures.incrementAndGet()
        }
    }

    fun addProcessingTime(durationMs: Long) {
        require(durationMs >= 0)
        totalProcessingTimeMs.addAndGet(durationMs)
    }

    // ========================
    // STAGE TIMING
    // ========================

    fun recordDownloadTime(durationMs: Long) {
        require(durationMs >= 0)
        downloadTimeMsTotal.addAndGet(durationMs)
    }

    fun recordTranscriptionTime(durationMs: Long) {
        require(durationMs >= 0)
        transcriptionTimeMsTotal.addAndGet(durationMs)
    }

    fun recordSummarizationTime(durationMs: Long) {
        require(durationMs >= 0)
        summarizationTimeMsTotal.addAndGet(durationMs)
    }

    // ========================
    // RESILIENCE METRICS (NOVO)
    // ========================

    fun incrementRetry(stage: String) {
        retriesByStage.computeIfAbsent(stage) { AtomicLong(0) }
            .incrementAndGet()
    }

    fun incrementTimeout(stage: String) {
        timeoutsByStage.computeIfAbsent(stage) { AtomicLong(0) }
            .incrementAndGet()
    }

    fun getRetries(stage: String): Long =
        retriesByStage[stage]?.get() ?: 0

    fun getTimeouts(stage: String): Long =
        timeoutsByStage[stage]?.get() ?: 0

    // ========================
    // BACKPRESSURE
    // ========================

    fun incrementQueueRejections() {
        ingestionQueueRejections.incrementAndGet()
    }

    fun getQueueRejections(): Long {
        return ingestionQueueRejections.get()
    }

    // ========================
    // SNAPSHOT (SEM QUEBRA)
    // ========================

    fun snapshot(): IngestionMetricsResponse {
        val v2 = snapshotV2()

        return IngestionMetricsResponse(
            ingestionJobsStarted = v2.started,
            ingestionJobsSucceeded = v2.succeeded,
            ingestionJobsFailed = v2.failed,
            ingestionProcessingTimeMsTotal = totalProcessingTimeMs.get(),

            ingestionJobsFailedTimeout = v2.failedTimeout,
            ingestionJobsFailedProcess = v2.failedProcess,
            ingestionJobsFailedUnknown = v2.failedUnknown,

            ingestionSuccessRate = v2.successRate,
            ingestionFailureRate = v2.failureRate,
            ingestionAvgProcessingTimeMs = v2.avgProcessingTimeMs.toDouble()
        )
    }

    fun snapshotV2(): IngestionMetricsSnapshot {
        val started = jobsStarted.get()
        val succeeded = jobsSucceeded.get()
        val failed = jobsFailed.get()

        val successRate =
            if (started > 0) succeeded.toDouble() / started else 0.0

        val failureRate =
            if (started > 0) failed.toDouble() / started else 0.0

        val avgProcessingTimeMs =
            if (succeeded > 0)
                totalProcessingTimeMs.get() / succeeded
            else
                0L

        return IngestionMetricsSnapshot(
            started = started,
            succeeded = succeeded,
            failed = failed,
            successRate = successRate,
            failureRate = failureRate,
            avgProcessingTimeMs = avgProcessingTimeMs,
            failedTimeout = timeoutFailures.get(),
            failedProcess = processFailures.get(),
            failedUnknown = unknownFailures.get(),

            rejected = ingestionQueueRejections.get(),

            downloadTimeMsTotal = downloadTimeMsTotal.get(),
            transcriptionTimeMsTotal = transcriptionTimeMsTotal.get(),
            summarizationTimeMsTotal = summarizationTimeMsTotal.get()
        )
    }
}