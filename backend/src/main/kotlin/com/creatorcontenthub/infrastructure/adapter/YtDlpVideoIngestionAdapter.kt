package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.IngestionResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.exception.DownloadTimeoutException
import com.creatorcontenthub.domain.exception.JobCanceledException
import com.creatorcontenthub.domain.util.YoutubeUrlUtils.extractVideoId
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.config.IngestionTimeoutConfig
import com.creatorcontenthub.infrastructure.exception.ProcessExecutionException
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.resilience.RetryUtil
import com.creatorcontenthub.application.resilience.ErrorClassifier
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.runtime.RuntimeEvent
import com.creatorcontenthub.infrastructure.runtime.RuntimeEventBus
import com.creatorcontenthub.infrastructure.storage.FileStorageService
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter(
    private val configuredPath: String? = "C:\\tools\\yt-dlp\\yt-dlp.exe",
    private val outputDir: String,
    private val timeoutConfig: IngestionTimeoutConfig = IngestionTimeoutConfig(),
    private val jobRepository: JobRepository,
    private val runtimeEventBus: RuntimeEventBus,
    private val fileStorageService: FileStorageService
) : VideoIngestionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 200
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun ingest(url: String, jobId: String): IngestionResult {

        val videoDir = File(outputDir, "video").apply {
            if (!exists() && !mkdirs()) {
                throw RuntimeException("Failed to create output directory: $absolutePath")
            }
        }

        val outputPathTemplate = "${videoDir.absolutePath}/$jobId.%(ext)s"

        val ytDlpCommand = resolveCommand()

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
            cleanupPreviousArtifacts(videoDir, jobId)
            var process: Process? = null
            var readerThread: Thread? = null

            try {

                process = ProcessBuilder(
                    ytDlpCommand,
                    "--extractor-args", "youtube:player_client=android",
                    "--restrict-filenames",
                    "--no-playlist",
                    "-o", outputPathTemplate,
                    url
                ).redirectErrorStream(true).start()

                val activeProcess = process ?: throw IllegalStateException("Process not initialized")

                logger.info("event=download_start jobId={} command={}", jobId, ytDlpCommand)

                var lastProgress = -1.0

                readerThread = Thread {
                    try {
                        BufferedReader(
                            InputStreamReader(
                                activeProcess.inputStream,
                                StandardCharsets.UTF_8
                            )
                        ).use { reader ->
                            var line: String?
                            var count = 0

                            while (reader.readLine().also { line = it } != null) {
                                val currentLine = line ?: break

                                val progressMatch = Regex("""(\d{1,3}\.\d+)%""")
                                    .find(currentLine)

                                val progress = progressMatch
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.toDoubleOrNull()

                                if (progress != null) {

                                    if (progress - lastProgress >= 5.0) {

                                        lastProgress = progress

                                        runtimeEventBus.publish(
                                            RuntimeEvent(
                                                jobId = jobId,
                                                event = "download_progress",
                                                stage = "DOWNLOADING",
                                                status = "PROCESSING",
                                                progress = progress,
                                                message = "Download progress"
                                            )
                                        )
                                    }
                                }

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

                logger.info(
                    "event=yt_dlp_output_summary jobId={} total_lines={} sample={}",
                    jobId,
                    outputLines.size,
                    outputLines.take(20)
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

        val outputFile = videoDir
            .listFiles()
            ?.firstOrNull { file ->
                file.name.startsWith("$jobId.")
            }
            ?: throw RuntimeException(
                "Video file not generated for jobId=$jobId"
            )

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
        val videoId = extractVideoId(url)

        val rawCachedJob = videoId?.let { jobRepository.findByVideoId(it) }

        val cachedJob = rawCachedJob
            ?.takeIf { it.status.isFinal() }

        if (videoId != null) {
            when {
                rawCachedJob == null -> {
                    logger.info(
                        "event=title_cache_miss jobId={} videoId={}",
                        jobId,
                        videoId
                    )
                }

                rawCachedJob.status.isFinal().not() -> {
                    logger.info(
                        "event=title_cache_ignored_non_final jobId={} videoId={} status={}",
                        jobId,
                        videoId,
                        rawCachedJob.status
                    )
                }
            }
        }

        val cachedTitle = cachedJob?.title

        if (!cachedTitle.isNullOrBlank()) {
            logger.info(
                "event=title_cache_hit_persistent jobId={} videoId={} title={}",
                jobId,
                videoId,
                cachedTitle
            )

            return IngestionResult(
                videoPath = outputFile.absolutePath,
                title = cachedTitle
            )
        }

        // ---------------- TITLE RESOLUTION ----------------
        val resolutionStart = System.currentTimeMillis()

        val jsonTitle = extractTitleUsingJson(url, jobId)

        val oembedTitle = if (jsonTitle == null) {
            extractTitleUsingOEmbed(url, jobId)
        } else null

        val parsedTitle = extractTitleFromOutput(outputLines, jobId)

        val finalTitle = jsonTitle ?: oembedTitle ?: parsedTitle

        val source = when {
            jsonTitle != null -> "JSON"
            oembedTitle != null -> "OEMBED"
            parsedTitle != null -> "FALLBACK"
            else -> "UNKNOWN"
        }

        logger.info(
            "event=title_source_resolved jobId={} videoId={} source={} duration_ms={}",
            jobId,
            videoId,
            source,
            System.currentTimeMillis() - resolutionStart
        )

        return IngestionResult(
            videoPath = outputFile.absolutePath,
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

    private fun extractTitleFromOutput(
        outputLines: List<String>,
        jobId: String
    ): String? {

        logger.info(
            "event=title_extraction_start jobId={} lines={}",
            jobId,
            outputLines.size
        )

        outputLines.forEach { line ->
            if (
                line.contains("title", ignoreCase = true) ||
                line.contains("[youtube]", ignoreCase = true)
            ) {
                logger.info(
                    "event=title_candidate jobId={} line={}",
                    jobId,
                    line
                )
            }
        }

        return outputLines
            .firstOrNull { it.contains("[download] Destination:") }
            ?.substringAfter("Destination:")
            ?.trim()
            ?.substringAfterLast("\\")
            ?.substringBeforeLast(".")
    }

    // NOVA FUNÇÃO — JSON
    private fun extractTitleUsingJson(
        url: String,
        jobId: String
    ): String? {

        val command = listOf(
            "yt-dlp",
            "--print-json",
            "--skip-download",
            "--no-playlist",
            "--js-runtimes", "node",
            "--sleep-interval", "2",
            "--max-sleep-interval", "5",
            url
        )

        logger.info(
            "event=title_json_start jobId={} command={}",
            jobId,
            command.joinToString(" ")
        )

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()

            val exitCode = process.waitFor()

            logger.info(
                "event=title_json_raw jobId={} exitCode={} output_sample={}",
                jobId,
                exitCode,
                output.take(500)
            )

            if (exitCode != 0) {
                logger.warn(
                    "event=title_json_failed jobId={} exitCode={}",
                    jobId,
                    exitCode
                )
                return null
            }

            val mapper = com.fasterxml.jackson.databind.ObjectMapper()

            val jsonLine = output
                .lineSequence()
                .firstOrNull { it.trim().startsWith("{") }

            if (jsonLine == null) {
                logger.warn(
                    "event=title_json_not_found jobId={} output_size={}",
                    jobId,
                    output.length
                )
                return null
            }

            val node = mapper.readTree(jsonLine)

            val title = node
                .get("title")
                ?.asText()
                ?.takeIf { it.isNotBlank() }

            logger.info(
                "event=title_json_extracted jobId={} title={}",
                jobId,
                title
            )

            title

        } catch (e: Exception) {
            logger.error(
                "event=title_json_exception jobId={} message={}",
                jobId,
                e.message,
                e
            )
            null
        }
    }

    private fun extractTitleUsingOEmbed(
        url: String,
        jobId: String
    ): String? {

        val oembedUrl = "https://www.youtube.com/oembed?url=$url&format=json"

        logger.info(
            "event=title_oembed_start jobId={} url={}",
            jobId,
            oembedUrl
        )

        return try {
            val connection = java.net.URL(oembedUrl).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val response = connection.getInputStream()
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()

            logger.info(
                "event=title_oembed_raw jobId={} response_sample={}",
                jobId,
                response.take(300)
            )

            val title = Regex("\"title\"\\s*:\\s*\"(.*?)\"")
                .find(response)
                ?.groupValues?.get(1)

            logger.info(
                "event=title_oembed_extracted jobId={} title={}",
                jobId,
                title
            )

            title

        } catch (e: Exception) {
            logger.warn(
                "event=title_oembed_failed jobId={} message={}",
                jobId,
                e.message
            )
            null
        }
    }
}