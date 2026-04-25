package com.creatorcontenthub.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class IngestionMicrometerMetrics {

    private val registry = PrometheusRegistry.registry

    // 🔢 Counters
    private val jobsStarted = Counter.builder("ingestion_jobs_started_total")
        .register(registry)

    private val jobsSucceeded = Counter.builder("ingestion_jobs_succeeded_total")
        .register(registry)

    private val jobsFailed = Counter.builder("ingestion_jobs_failed_total")
        .tag("error_type", "unknown")
        .register(registry)

    // ⏱ Timers
    private val downloadTimer = Timer.builder("ingestion_download_duration")
        .publishPercentileHistogram()
        .register(registry)

    private val transcriptionTimer = Timer.builder("ingestion_transcription_duration")
        .publishPercentileHistogram()
        .register(registry)

    private val summarizationTimer = Timer.builder("ingestion_summarization_duration")
        .publishPercentileHistogram()
        .register(registry)

    private val totalTimer = Timer.builder("ingestion_total_duration")
        .publishPercentileHistogram()
        .register(registry)

    // 🔹 Counters API
    fun incrementStarted() = jobsStarted.increment()

    fun incrementSucceeded() = jobsSucceeded.increment()

    fun incrementFailed(errorType: String) {
        Counter.builder("ingestion_jobs_failed_total")
            .tag("error_type", errorType)
            .register(registry)
            .increment()
    }

    // 🔹 Timers API
    fun recordDownload(durationMs: Long) {
        downloadTimer.record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordTranscription(durationMs: Long) {
        transcriptionTimer.record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordSummarization(durationMs: Long) {
        summarizationTimer.record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordTotal(durationMs: Long) {
        totalTimer.record(durationMs, TimeUnit.MILLISECONDS)
    }
}