package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.exception.DownloadTimeoutException
import com.creatorcontenthub.domain.model.ErrorClassifier
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.config.IngestionTimeoutConfig
import com.creatorcontenthub.infrastructure.resilience.RetryUtil
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter(
    private val configuredPath: String? = null,
    private val outputDir: String,
    private val timeoutConfig: IngestionTimeoutConfig = IngestionTimeoutConfig()
) : VideoIngestionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 200
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun ingest(url: String, jobId: String): String {

        val outputDirFile = File(outputDir)

        if (!outputDirFile.exists() && !outputDirFile.mkdirs()) {
            throw RuntimeException("Failed to create output directory: $outputDir")
        }

        val outputPathTemplate = "$outputDir/$jobId.%(ext)s"

        // 🧹 idempotência
        outputDirFile
            .listFiles { _, name -> name.startsWith(jobId) }
            ?.forEach { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        logger.error("event=cleanup_failed jobId={} file={}", jobId, file.name)
                        throw IllegalStateException("Failed to delete: ${file.absolutePath}")
                    }
                    logger.warn("event=cleanup_previous_artifact jobId={} file={}", jobId, file.name)
                }
            }

        val ytDlpCommand = resolveCommand()

        RetryUtil.retry(
            maxAttempts = 3,
            initialDelayMs = 1000,
            shouldRetry = { throwable ->
                val type = ErrorClassifier.classify(throwable = throwable)
                shouldRetry(type)
            },
            stage = "download",
            jobId = jobId
        ) {

            var process: Process? = null
            var readerThread: Thread? = null

            try {

                process = try {
                    ProcessBuilder(
                        ytDlpCommand,
                        "-x",
                        "--audio-format", "mp3",
                        "--extractor-args", "youtube:player_client=android",
                        "--restrict-filenames",
                        "-o", outputPathTemplate,
                        url
                    )
                        .redirectErrorStream(true)
                        .start()
                } catch (e: Exception) {
                    val type = ErrorClassifier.classify(throwable = e)

                    logger.error(
                        "event=download_dependency_failure jobId={} errorType={} message={}",
                        jobId, type, e.message
                    )

                    throw e
                }

                val activeProcess = process ?: throw IllegalStateException("Process not initialized")

                logger.info("event=download_start jobId={} command={}", jobId, ytDlpCommand)

                val outputLines = mutableListOf<String>()

                readerThread = Thread {
                    BufferedReader(InputStreamReader(activeProcess.inputStream)).use { reader ->
                        var line: String?
                        var count = 0

                        while (!Thread.currentThread().isInterrupted) {
                            line = reader.readLine() ?: break

                            if (count < MAX_OUTPUT_LINES) {
                                outputLines.add(line)
                            }
                            count++
                        }
                    }
                }

                readerThread.start()

                val finished = activeProcess.waitFor(
                    timeoutConfig.downloadTimeoutMs,
                    TimeUnit.MILLISECONDS
                )

                if (!finished) {
                    val errorType = ErrorClassifier.classify(
                        throwable = java.util.concurrent.TimeoutException()
                    )

                    logger.error(
                        "event=download_timeout jobId={} errorType={} timeoutMs={}",
                        jobId,
                        errorType,
                        timeoutConfig.downloadTimeoutMs
                    )

                    throw DownloadTimeoutException(
                        "yt-dlp timeout after ${timeoutConfig.downloadTimeoutMs} ms"
                    )
                }

                readerThread.join(5000)

                val exitCode = activeProcess.exitValue()

                if (exitCode != 0) {
                    val sanitized = outputLines.joinToString("\n").take(500)
                    val errorType = ErrorClassifier.classify(exitCode = exitCode)

                    logger.error(
                        "event=download_failed jobId={} exitCode={} errorType={} output={}",
                        jobId,
                        exitCode,
                        errorType,
                        sanitized
                    )

                    if (shouldRetry(errorType)) {
                        throw RuntimeException("retryable download failure: $errorType")
                    } else {
                        throw IllegalStateException("non-retryable download failure: $errorType")
                    }
                }

            } finally {
                process?.destroyForcibly()
                readerThread?.interrupt()
                readerThread?.join(1000)
            }
        }

        val outputFile = File("$outputDir/$jobId.mp3")

        if (!outputFile.exists()) {
            logger.error("event=download_file_missing jobId={} path={}", jobId, outputFile.absolutePath)
            throw RuntimeException("Audio file not generated at ${outputFile.absolutePath}")
        }

        logger.info("event=download_success jobId={} path={}", jobId, outputFile.absolutePath)

        return outputFile.absolutePath
    }

    private fun resolveCommand(): String {
        if (!configuredPath.isNullOrBlank()) {
            val file = File(configuredPath)
            if (file.exists()) return file.absolutePath

            logger.warn("Configured yt-dlp not found at {}", configuredPath)
        }
        return "yt-dlp"
    }

    private fun shouldRetry(errorType: ErrorType): Boolean {
        return when (errorType) {
            ErrorType.TIMEOUT -> true
            ErrorType.DEPENDENCY_FAILURE -> true
            ErrorType.PROCESS_ERROR -> false
            ErrorType.UNKNOWN -> false
        }
    }
}