package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.metrics.IngestionEvent
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.IngestionWindowMetrics
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter(
    private val executor: ExecutorService,
    private val jobStateStore: InMemoryJobStatusStore,
    private val metrics: IngestionMetrics,
    private val ingestionWindowMetrics: IngestionWindowMetrics
) : VideoIngestionPort {

    override fun ingest(url: String, jobId: String) {

        executor.submit {

            // 🔥 ESSENCIAL: contabiliza início do job
            metrics.incrementStarted()

            val startTime = System.currentTimeMillis()
            var success = false

            try {

                val outputDir = File("/tmp/creator-content-hub")
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                val outputPath = "${outputDir.absolutePath}/$jobId.mp3"

                val processBuilder = ProcessBuilder(
                    "yt-dlp",
                    "-x",
                    "--audio-format", "mp3",
                    "-o", outputPath,
                    url
                )

                processBuilder.redirectErrorStream(true)

                println("yt-dlp download started (jobId=$jobId)")

                val process = processBuilder.start()

                val output = process.inputStream.bufferedReader().use { it.readText() }

                val finished = process.waitFor(10, TimeUnit.MINUTES)

                // 🔴 TIMEOUT
                if (!finished) {
                    process.destroyForcibly()

                    jobStateStore.markFailed(
                        jobId,
                        ErrorType.TIMEOUT,
                        "Process timed out"
                    )

                    metrics.incrementFailed()
                    metrics.incrementFailedByType(ErrorType.TIMEOUT)

                    return@submit
                }

                val exitCode = process.exitValue()

                // 🔴 PROCESS ERROR
                if (exitCode != 0) {

                    val sanitized = output.take(300)

                    jobStateStore.markFailed(
                        jobId,
                        ErrorType.PROCESS_ERROR,
                        "yt-dlp exited with code $exitCode: $sanitized"
                    )

                    metrics.incrementFailed()
                    metrics.incrementFailedByType(ErrorType.PROCESS_ERROR)

                    return@submit
                }

                // 🟢 SUCCESS
                println("yt-dlp download completed (jobId=$jobId)")

                jobStateStore.markDone(jobId)

                metrics.incrementSucceeded()
                success = true

            } catch (ex: Exception) {

                val message = ex.message?.take(300) ?: "Unexpected error"

                println("yt-dlp failed (jobId=$jobId, error=$message)")

                jobStateStore.markFailed(
                    jobId,
                    ErrorType.UNKNOWN,
                    message
                )

                metrics.incrementFailed()
                metrics.incrementFailedByType(ErrorType.UNKNOWN)

            } finally {

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                val event = IngestionEvent(
                    timestamp = endTime,
                    success = success,
                    processingTimeMs = duration
                )

                // 🔥 registro da janela deslizante
                ingestionWindowMetrics.record(event)

                // 🔥 métricas acumuladas
                metrics.addProcessingTime(duration)
            }
        }
    }
}