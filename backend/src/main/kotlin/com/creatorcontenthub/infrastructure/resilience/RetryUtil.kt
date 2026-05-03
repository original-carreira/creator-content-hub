package com.creatorcontenthub.infrastructure.resilience

import com.creatorcontenthub.application.resilience.ErrorClassifier
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory

object RetryUtil {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun <T> retry(
        maxAttempts: Int,
        initialDelayMs: Long,
        shouldRetry: (Throwable) -> Boolean,
        stage: String,
        jobId: String,
        metrics: IngestionMetrics? = null,
        block: suspend () -> T
    ): T {

        var attempt = 1

        while (true) {
            try {
                return block()
            } catch (e: Throwable) {

                // ✅ 1. Cancelamento tem prioridade absoluta
                if (e is CancellationException) {
                    throw e
                }

                val errorType = ErrorClassifier.classify(
                    throwable = e,
                    message = e.message
                )

                // ✅ 2. Regra centralizada por tipo de erro
                val retryableByType = when (errorType) {
                    ErrorType.TIMEOUT,
                    ErrorType.DEPENDENCY_FAILURE -> true

                    ErrorType.PROCESS_ERROR,
                    ErrorType.UNKNOWN -> false
                }

                // ❌ 3. Condição de parada (fail-fast)
                if (attempt >= maxAttempts || !retryableByType || !shouldRetry(e)) {

                    if (errorType == ErrorType.TIMEOUT) {
                        metrics?.incrementTimeout(stage)
                    }

                    // 🆕 métrica por tipo de falha
                    metrics?.incrementFailureByType(stage, errorType.name)

                    throw e
                }

                // ✅ 4. Backoff exponencial
                val baseDelay = initialDelayMs * (1L shl (attempt - 1))

                // ✅ 5. Jitter (reduz thundering herd)
                val jitterBound = (baseDelay / 2).coerceAtLeast(1)
                val jitter = kotlin.random.Random.nextLong(0, jitterBound)

                val finalDelay = baseDelay + jitter

                // 🧾 Log estruturado
                logger.warn(
                    "event=retry jobId={} stage={} attempt={} delayMs={} errorType={} error={}",
                    jobId,
                    stage,
                    attempt,
                    finalDelay,
                    errorType,
                    e.message
                )

                // 📊 métrica de retry
                metrics?.incrementRetry(stage)

                kotlin.coroutines.coroutineContext.ensureActive()
                delay(finalDelay)

                attempt++
            }
        }
    }
}