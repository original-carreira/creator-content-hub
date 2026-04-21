package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.metrics.IngestionEvent
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.IngestionWindowMetrics
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter(
    private val executor: ExecutorService,
    private val jobStateStore: InMemoryJobStatusStore,
    private val metrics: IngestionMetrics,
    private val ingestionWindowMetrics: IngestionWindowMetrics
) : VideoIngestionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 200
    }

    override fun ingest(url: String, jobId: String) {

        executor.submit {

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

                // ✅ leitura segura (limitada)
                val outputLines = mutableListOf<String>()
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    var count = 0

                    while (reader.readLine().also { line = it } != null) {
                        if (count < MAX_OUTPUT_LINES) {
                            outputLines.add(line!!)
                        }
                        count++
                    }
                }

                val finished = process.waitFor(10, TimeUnit.MINUTES)

                // 🔴 TIMEOUT
                if (!finished) {
                    process.destroyForcibly()

                    jobStateStore.markFailed(
                        jobId,
                        ErrorType.TIMEOUT,
                        "Process timed out"
                    )

                    metrics.incrementFailed(ErrorType.TIMEOUT)

                    return@submit
                }

                val exitCode = process.exitValue()

                // 🔴 PROCESS ERROR
                if (exitCode != 0) {

                    val sanitized = outputLines.joinToString("\n").take(300)

                    jobStateStore.markFailed(
                        jobId,
                        ErrorType.PROCESS_ERROR,
                        "yt-dlp exited with code $exitCode: $sanitized"
                    )

                    metrics.incrementFailed(ErrorType.PROCESS_ERROR)

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

                metrics.incrementFailed(ErrorType.UNKNOWN)

            } finally {

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                val event = IngestionEvent(
                    timestamp = endTime,
                    success = success,
                    processingTimeMs = duration
                )

                ingestionWindowMetrics.record(event)
                metrics.addProcessingTime(duration)
            }
        }
    }
}