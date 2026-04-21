package com.creatorcontenthub.infrastructure.metrics

import com.creatorcontenthub.application.dto.IngestionWindowMetricsSnapshot
import java.util.concurrent.ConcurrentLinkedQueue

class IngestionWindowMetrics {

    private val events = ConcurrentLinkedQueue<IngestionEvent>()

    private val windowMillis = 5 * 60 * 1000L // 5 minutos

    fun record(event: IngestionEvent) {
        events.add(event)
        cleanup()
    }

    fun snapshot(): IngestionWindowMetricsSnapshot {
        cleanup()

        val now = System.currentTimeMillis()
        val threshold = now - windowMillis

        var started = 0L
        var succeeded = 0L
        var totalProcessingTime = 0L
        var successCount = 0L

        for (event in events) {
            if (event.timestamp < threshold) continue

            started++

            if (event.success) {
                succeeded++
                totalProcessingTime += event.processingTimeMs
                successCount++
            }
        }

        val failed = started - succeeded

        val successRate = if (started > 0) succeeded.toDouble() / started else 0.0
        val failureRate = if (started > 0) failed.toDouble() / started else 0.0

        val avgProcessingTimeMs =
            if (successCount > 0) totalProcessingTime / successCount else 0L

        return IngestionWindowMetricsSnapshot(
            windowSizeSeconds = windowMillis / 1000,
            started = started,
            succeeded = succeeded,
            failed = failed,
            successRate = successRate,
            failureRate = failureRate,
            avgProcessingTimeMs = avgProcessingTimeMs
        )
    }

    private fun cleanup() {
        val threshold = System.currentTimeMillis() - windowMillis

        while (true) {
            val head = events.peek() ?: break
            if (head.timestamp >= threshold) break
            events.poll()
        }
    }
}