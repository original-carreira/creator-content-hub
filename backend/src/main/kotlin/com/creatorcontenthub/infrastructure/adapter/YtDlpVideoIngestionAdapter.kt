package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.IngestionResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.exception.DownloadTimeoutException
import com.creatorcontenthub.domain.exception.JobCanceledException
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.config.IngestionTimeoutConfig
import com.creatorcontenthub.infrastructure.exception.ProcessExecutionException
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.resilience.RetryUtil
import com.creatorcontenthub.application.resilience.ErrorClassifier
import com.creatorcontenthub.domain.model.ErrorType
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter(
    private val configuredPath: String? = "C:\\tools\\yt-dlp\\yt-dlp.exe",
    private val outputDir: String,
    private val timeoutConfig: IngestionTimeoutConfig = IngestionTimeoutConfig(),
    private val jobRepository: JobRepository
) : VideoIngestionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 200
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun ingest(url: String, jobId: String): IngestionResult {

        val audioDir = File(outputDir, "audio").apply {
            if (!exists() && !mkdirs()) {
                throw RuntimeException("Failed to create output directory: $absolutePath")
            }
        }

        val outputPathTemplate = "${audioDir.absolutePath}/$jobId.%(ext)s"

        val ytDlpCommand = resolveCommand()

        // =============================
        // EXTRAIR TÍTULO DO VÍDEO
        // =============================
        val title = try {
            val process = ProcessBuilder(
                ytDlpCommand,
                "--print", "title",
                "--cookies-from-browser", "firefox",
                url
            ).redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()

            val exitCode = process.waitFor()

            if (exitCode != 0 || output.isBlank()) {
                logger.warn("event=title_fetch_failed jobId={} exitCode={} output={}", jobId, exitCode, output)
                null
            } else {
                output.lineSequence().firstOrNull()
            }

        } catch (e: Exception) {
            logger.warn("event=title_fetch_exception jobId={} message={}", jobId, e.message)
            null
        }
        val outputLines = Collections.synchronizedList(mutableListOf<String>())

        RetryUtil.retry(
            maxAttempts = 3,
            initialDelayMs = 1000,
            shouldRetry = { throwable ->
                val errorType = ErrorClassifier.classify(
                    throwable = throwable,
                    message = throwable.message
                )

                when (errorType) {
                    ErrorType.TIMEOUT,
                    ErrorType.DEPENDENCY_FAILURE -> true

                    ErrorType.PROCESS_ERROR,
                    ErrorType.UNKNOWN -> false
                }
            },
            stage = "download",
            jobId = jobId
        ) {
            cleanupPreviousArtifacts(audioDir, jobId)
            var process: Process? = null
            var readerThread: Thread? = null

            try {

                process = ProcessBuilder(
                    ytDlpCommand,
                    "-x",
                    "--audio-format", "mp3",
                    "--extractor-args", "youtube:player_client=android",
                    "--restrict-filenames",
                    "--no-playlist",
                    "-o", outputPathTemplate,
                    url
                ).redirectErrorStream(true).start()

                val activeProcess = process ?: throw IllegalStateException("Process not initialized")

                logger.info("event=download_start jobId={} command={}", jobId, ytDlpCommand)

                readerThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(activeProcess.inputStream)).use { reader ->
                            var line: String?
                            var count = 0

                            while (reader.readLine().also { line = it } != null) {
                                val currentLine = line ?: break

                                logger.info(
                                    "event=yt_dlp_output jobId={} line={}",
                                    jobId,
                                    currentLine
                                )

                                if (count < MAX_OUTPUT_LINES) {
                                    outputLines.add(currentLine)
                                }
                                count++
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "event=yt_dlp_reader_error jobId={} message={}",
                            jobId,
                            e.message,
                            e
                        )
                    }
                }

                readerThread.start()

                val startTime = System.currentTimeMillis()
                val timeoutMs = timeoutConfig.downloadTimeoutMs

                // LOOP CORRETO (sem isAlive)
                while (true) {

                    val finished = activeProcess.waitFor(1, TimeUnit.SECONDS)

                    if (finished) break

                    val job = jobRepository.findById(jobId)

                    if (job?.status == JobStatus.CANCELED) {

                        StructuredLogger.log(
                            logger = logger,
                            event = "job_cancel_detected",
                            jobId = jobId,
                            requestId = "internal",
                            status = "CANCELED",
                            extra = mapOf("stage" to "download")
                        )

                        activeProcess.destroyForcibly()
                        readerThread?.interrupt()

                        StructuredLogger.log(
                            logger = logger,
                            event = "process_killed",
                            jobId = jobId,
                            requestId = "internal",
                            status = "CANCELED",
                            extra = mapOf("stage" to "download", "process" to "yt-dlp")
                        )

                        throw JobCanceledException()
                    }

                    val elapsed = System.currentTimeMillis() - startTime

                    if (elapsed > timeoutMs) {
                        activeProcess.destroyForcibly()
                        readerThread?.interrupt()

                        logger.error(
                            "event=download_timeout jobId={} timeoutMs={}",
                            jobId,
                            timeoutMs
                        )

                        throw DownloadTimeoutException("yt-dlp timeout after $timeoutMs ms")
                    }
                }

                logger.info(
                    "event=yt_dlp_full_output jobId={} output={}",
                    jobId,
                    outputLines.joinToString("\n")
                )

                val exitCode = activeProcess.exitValue()

                if (exitCode != 0) {

                    val errorLog = outputLines.joinToString("\n")

                    logger.error(
                        "event=download_failed jobId={} exitCode={} output={}",
                        jobId,
                        exitCode,
                        errorLog
                    )

                    throw ProcessExecutionException(
                        exitCode = exitCode,
                        message = errorLog
                    )
                }

            } finally {

                if (process != null) {
                    if (process.isAlive) {
                        process.destroyForcibly()

                        logger.warn(
                            "event=process_force_killed jobId={} stage=download",
                            jobId
                        )
                    }
                }

                // NÃO interrompe → apenas garante finalização segura
                readerThread?.join(5_000)
                if (readerThread?.isAlive == true) {
                    readerThread.interrupt()
                    logger.warn(
                        "event=reader_thread_stuck jobId={} stage=download",
                        jobId
                    )
                }
            }
        }

        val outputFile = File(audioDir, "$jobId.mp3")

        if (!outputFile.exists() || outputFile.length() == 0L) {
            logger.error(
                "event=download_file_invalid jobId={} path={}",
                jobId,
                outputFile.absolutePath
            )
            throw RuntimeException("Audio file not generated or empty at ${outputFile.absolutePath}")
        }

        logger.info(
            "event=download_success jobId={} path={}",
            jobId,
            outputFile.absolutePath
        )

        val finalTitle = title ?: extractTitleFromOutput(outputLines)

        return IngestionResult(
            audioPath = outputFile.absolutePath,
            title = finalTitle
        )
    }

    private fun resolveCommand(): String {
        if (!configuredPath.isNullOrBlank()) {
            val file = File(configuredPath)
            if (file.exists()) return file.absolutePath

            logger.warn("Configured yt-dlp path not found: {}. Using system PATH.", configuredPath)
        }
        return "yt-dlp"
    }

    private fun cleanupPreviousArtifacts(directory: File, jobId: String) {
        directory.listFiles { _, name -> name.startsWith(jobId) }?.forEach { file ->
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) {
                    logger.error("event=cleanup_failed jobId={} file={}", jobId, file.name)
                    throw IllegalStateException("Failed to delete: ${file.absolutePath}")
                }
                logger.warn("event=cleanup_previous_artifact jobId={} file={}", jobId, file.name)
            }
        }
    }

    private fun extractTitleFromOutput(outputLines: List<String>): String? {
        return outputLines
            .firstOrNull { it.contains("[download] Destination:") }
            ?.substringAfter("Destination:")
            ?.trim()
            ?.substringAfterLast("\\")
            ?.substringBeforeLast(".")
    }
}