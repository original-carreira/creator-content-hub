package com.creatorcontenthub.infrastructure.metrics

import com.creatorcontenthub.application.dto.IngestionMetricsResponse
import com.creatorcontenthub.application.dto.IngestionMetricsSnapshot
import com.creatorcontenthub.domain.model.ErrorType
import java.util.concurrent.atomic.AtomicLong

/**
 * Métricas de ingestão de mídia.
 *
 * Thread-safe via AtomicLong.
 * Sem dependências externas.
 *
 * Definições:
 *
 * - ingestion_jobs_started:
 *   Total de jobs iniciados APÓS validação bem-sucedida do request.
 *
 * - ingestion_jobs_succeeded:
 *   Total de jobs concluídos com sucesso (exitCode == 0).
 *
 * - ingestion_jobs_failed:
 *   Total de jobs que falharam, incluindo:
 *     - exceções durante execução
 *     - timeout
 *     - exitCode diferente de 0
 *
 * - ingestion_processing_time_ms_total:
 *   Soma acumulada do tempo de processamento de TODOS os jobs (em milissegundos).
 *
 *   ⚠️ IMPORTANTE:
 *   Esta métrica NÃO representa latência média.
 *
 *   Para calcular a latência média:
 *
 *     avg_latency = totalProcessingTimeMs / jobsSucceeded
 *
 *   (considerando apenas execuções bem-sucedidas)
 */
class IngestionMetrics {

    private val jobsStarted = AtomicLong(0)
    private val jobsSucceeded = AtomicLong(0)
    private val jobsFailed = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)

    // Classificação de falhas
    private val timeoutFailures = AtomicLong(0)
    private val processFailures = AtomicLong(0)
    private val unknownFailures = AtomicLong(0)

    fun incrementStarted() {
        jobsStarted.incrementAndGet()
    }

    fun incrementSucceeded() {
        jobsSucceeded.incrementAndGet()
    }

    fun incrementFailed() {
        jobsFailed.incrementAndGet()
    }

    fun incrementFailedByType(errorType: ErrorType) {
        when (errorType) {
            ErrorType.TIMEOUT -> timeoutFailures.incrementAndGet()
            ErrorType.PROCESS_ERROR -> processFailures.incrementAndGet()
            ErrorType.UNKNOWN -> unknownFailures.incrementAndGet()
        }
    }

    fun addProcessingTime(durationMs: Long) {
        totalProcessingTimeMs.addAndGet(durationMs)
    }

    /**
     * 🔴 SNAPSHOT LEGADO (AGORA DELEGA PARA V2)
     *
     * Sem lógica própria → evita divergência
     */
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

    /**
     * 🟢 FONTE DE VERDADE (V2)
     */
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
            failedUnknown = unknownFailures.get()
        )
    }
}