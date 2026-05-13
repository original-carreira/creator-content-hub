package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.TranscriptionResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.TranscriptionPort
import com.creatorcontenthub.domain.exception.JobCanceledException
import com.creatorcontenthub.domain.exception.TranscriptionTimeoutException
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.resilience.WhisperConcurrencyLimiter
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.TimeUnit

class WhisperTranscriptionAdapter(
    private val configuredPath: String? = null,
    private val timeoutMinutes: Long = 10,
    private val jobRepository: JobRepository
) : TranscriptionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 300
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun transcribe(audioPath: String, jobId: String): TranscriptionResult {
        return WhisperConcurrencyLimiter.withPermitBlocking {

            val start = System.currentTimeMillis()

            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                logger.error("event=transcription_file_missing jobId={} path={}", jobId, audioPath)
                throw RuntimeException("Audio file not found: $audioPath")
            }

            val baseDir = audioFile.parentFile?.parentFile ?: audioFile.parentFile
            val outputDir = File(baseDir, "transcription").apply {
                if (!exists() && !mkdirs()) {
                    throw RuntimeException("Failed to create transcription directory")
                }
            }

            cleanupPreviousTranscriptions(outputDir, jobId)

            val command = listOf(
                resolveCommand(),
                audioPath,
                "--output_format", "txt",
                "--output_dir", outputDir.absolutePath,
                "--fp16", "False"
            )

            logger.info(
                "event=transcription_start jobId={} path={} command={}",
                jobId,
                audioPath,
                command.joinToString(" ")
            )

            var process: Process? = null

            try {
                process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val activeProcess = process

                val outputLines = Collections.synchronizedList(mutableListOf<String>())

                val readerThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(activeProcess.inputStream, Charsets.UTF_8)).use { reader ->
                            var line: String?
                            var count = 0

                            while (reader.readLine().also { line = it } != null) {
                                val currentLine = line ?: break

                                if (count < MAX_OUTPUT_LINES) {
                                    outputLines.add(currentLine)
                                }
                                count++
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "event=whisper_reader_error jobId={} message={}",
                            jobId,
                            e.message,
                            e
                        )
                    }
                }

                readerThread.start()

                val timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes)

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
                            extra = mapOf("stage" to "transcription")
                        )

                        activeProcess.destroyForcibly()
                        readerThread?.interrupt()

                        StructuredLogger.log(
                            logger = logger,
                            event = "process_killed",
                            jobId = jobId,
                            requestId = "internal",
                            status = "CANCELED",
                            extra = mapOf(
                                "stage" to "transcription",
                                "process" to "whisper"
                            )
                        )

                        throw JobCanceledException()
                    }

                    val elapsed = System.currentTimeMillis() - start

                    if (elapsed > timeoutMs) {
                        activeProcess.destroyForcibly()
                        readerThread?.interrupt()

                        logger.error(
                            "event=transcription_timeout jobId={} timeoutMinutes={}",
                            jobId,
                            timeoutMinutes
                        )

                        throw TranscriptionTimeoutException(
                            "Transcription timed out after $timeoutMinutes minutes"
                        )
                    }
                }

                readerThread.join(5_000)

                if (readerThread.isAlive) {
                    logger.warn("event=whisper_reader_thread_stuck jobId={}", jobId)
                    readerThread.interrupt()
                }

                logger.info(
                    "event=whisper_full_output jobId={} output={}",
                    jobId,
                    outputLines.joinToString("\n")
                )

                val exitCode = activeProcess.exitValue()

                if (exitCode != 0) {
                    val error = outputLines.joinToString("\n")

                    logger.error(
                        "event=transcription_failed jobId={} exitCode={} fullOutput={}",
                        jobId,
                        exitCode,
                        error
                    )

                    throw RuntimeException("Whisper failed (code=$exitCode): $error")
                }

                val expectedFile = outputDir.listFiles { _, name ->
                    name.startsWith(audioFile.nameWithoutExtension) && name.endsWith(".txt")
                }?.maxByOrNull { it.lastModified() }
                    ?: throw RuntimeException("Transcription output file not found")

                if (!expectedFile.exists() || expectedFile.length() == 0L) {
                    throw RuntimeException("Transcription file is missing or empty")
                }

                val text = expectedFile.readText(Charsets.UTF_8).trim()

                if (text.isBlank()) {
                    throw RuntimeException("Transcription result is empty")
                }

                val totalDuration = System.currentTimeMillis() - start

                logger.info(
                    "event=transcription_success jobId={} durationMs={}",
                    jobId,
                    totalDuration
                )

                TranscriptionResult(
                    text = text,
                    durationMs = totalDuration
                )

            } finally {
                if (process?.isAlive == true) {
                    process.destroyForcibly()

                    logger.warn(
                        "event=process_force_killed jobId={} stage=transcription",
                        jobId
                    )
                }
            }
        }
    }

    private fun resolveCommand(): String {
        if (!configuredPath.isNullOrBlank()) {
            val file = File(configuredPath)
            if (file.exists()) return file.absolutePath

            logger.warn("Configured whisper path not found: {}. Using system PATH.", configuredPath)
        }
        return "whisper"
    }

    private fun cleanupPreviousTranscriptions(directory: File, jobId: String) {
        directory.listFiles { _, name ->
            name.startsWith(jobId) && name.endsWith(".txt")
        }?.forEach {
            try {
                it.delete()
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}