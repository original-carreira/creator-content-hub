package com.creatorcontenthub.infrastructure.metrics

import com.creatorcontenthub.domain.model.ErrorType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class IngestionMicrometerMetrics {

    private val registry = PrometheusRegistry.registry

    // 🔢 Counters Fixos
    private val jobsStarted = Counter.builder("ingestion_jobs_started_total")
        .register(registry)

    private val jobsSucceeded = Counter.builder("ingestion_jobs_succeeded_total")
        .register(registry)

    private val jobsCanceled = Counter.builder("ingestion_jobs_canceled_total")
        .description("Total de jobs cancelados")
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

    private val stageTimers = mutableMapOf<String, Timer>()

    // 🔹 Counters API
    fun incrementStarted() = jobsStarted.increment()

    fun incrementSucceeded() = jobsSucceeded.increment()

    fun incrementCanceled() = jobsCanceled.increment()

    // Ajustado para aceitar ErrorType do domínio e evitar erro no UseCase
    fun incrementFailed(errorType: ErrorType) {
        Counter.builder("ingestion_jobs_failed_total")
            .description("Total de falhas na ingestão por tipo de erro")
            .tag("error_type", errorType.name.lowercase())
            .register(registry)
            .increment()
    }

    // Mantido para compatibilidade caso precise passar String diretamente
    fun incrementFailed(errorType: String) {
        Counter.builder("ingestion_jobs_failed_total")
            .tag("error_type", errorType.lowercase())
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

    fun recordStage(stage: String, durationMs: Long) {
        val timer = stageTimers.getOrPut(stage) {
            Timer.builder("ingestion_stage_duration")
                .tag("stage", stage)
                .publishPercentileHistogram()
                .register(registry)
        }

        timer.record(durationMs, TimeUnit.MILLISECONDS)
    }
}
