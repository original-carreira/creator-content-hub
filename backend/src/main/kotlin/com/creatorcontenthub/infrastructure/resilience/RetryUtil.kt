package com.creatorcontenthub.infrastructure.resilience

import com.creatorcontenthub.domain.model.ErrorClassifier
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import org.slf4j.LoggerFactory
import kotlin.math.pow

object RetryUtil {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T> retry(
        maxAttempts: Int,
        initialDelayMs: Long,
        shouldRetry: (Throwable) -> Boolean,
        stage: String,
        jobId: String,
        metrics: IngestionMetrics? = null, // ✅ NOVO (opcional)
        block: () -> T
    ): T {

        var attempt = 1

        while (true) {
            try {
                return block()
            } catch (e: Throwable) {

                val errorType = ErrorClassifier.classify(throwable = e)

                // ❗ Se não vai retry, registrar timeout (se aplicável) e falhar
                if (attempt >= maxAttempts || !shouldRetry(e)) {

                    if (errorType == ErrorType.TIMEOUT) {
                        metrics?.incrementTimeout(stage)
                    }

                    throw e
                }

                val baseDelay = initialDelayMs * (1L shl (attempt - 1))

                val jitterBound = (baseDelay / 2).coerceAtLeast(1)
                val jitter = kotlin.random.Random.nextLong(0, jitterBound)

                val finalDelay = baseDelay + jitter

                logger.warn(
                    "event=retry jobId={} stage={} attempt={} delayMs={} errorType={}",
                    jobId,
                    stage,
                    attempt,
                    finalDelay,
                    errorType
                )

                // 🆕 MÉTRICA DE RETRY
                metrics?.incrementRetry(stage)

                try {
                    Thread.sleep(finalDelay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw ie
                }

                attempt++
            }
        }
    }
}