package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.TranscriptionResult
import com.creatorcontenthub.application.port.TranscriptionPort
import com.creatorcontenthub.domain.exception.TranscriptionTimeoutException
import com.creatorcontenthub.infrastructure.resilience.WhisperConcurrencyLimiter
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class WhisperTranscriptionAdapter(
    private val configuredPath: String? = null,
    private val timeoutMinutes: Long = 10
) : TranscriptionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 300
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun transcribe(audioPath: String): TranscriptionResult {

        val start = System.currentTimeMillis()

        WhisperConcurrencyLimiter.acquire()

        try {

            val audioFile = File(audioPath)

            if (!audioFile.exists()) {
                logger.error("event=transcription_file_missing path={}", audioPath)
                throw RuntimeException("Audio file not found: $audioPath")
            }

            val outputDir = audioFile.parentFile

            outputDir.listFiles { _, name ->
                name.startsWith(audioFile.nameWithoutExtension) && name.endsWith(".txt")
            }?.forEach { it.delete() }

            val command = listOf(
                resolveCommand(),
                audioPath,
                "--output_format", "txt",
                "--output_dir", outputDir.absolutePath
            )

            logger.info(
                "event=transcription_start path={} command={}",
                audioPath,
                command.joinToString(" ")
            )

            var process: Process? = null
            var readerThread: Thread? = null

            try {

                process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val outputLines = mutableListOf<String>()

                readerThread = Thread {
                    BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        var count = 0

                        while (!Thread.currentThread().isInterrupted) {
                            line = reader.readLine() ?: break

                            logger.debug("event=whisper_output line={}", line)

                            if (count < MAX_OUTPUT_LINES) {
                                outputLines.add(line)
                            }
                            count++
                        }
                    }
                }

                readerThread.start()

                val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

                if (!finished) {
                    throw TranscriptionTimeoutException("timeout")
                }

                readerThread.join(5000)

                val exitCode = process.exitValue()

                if (exitCode != 0) {
                    val error = outputLines.joinToString("\n").take(500)

                    logger.error(
                        "event=transcription_failed exitCode={} output={}",
                        exitCode,
                        error
                    )

                    throw RuntimeException("Whisper failed (code=$exitCode): $error")
                }

                val candidates = outputDir.listFiles { _, name ->
                    name.endsWith(".txt") && name.startsWith(audioFile.nameWithoutExtension)
                } ?: emptyArray()

                val expectedFile = candidates.maxByOrNull { it.lastModified() }
                    ?: throw RuntimeException("Transcription output not found")

                val text = expectedFile.readText().trim()
                val duration = System.currentTimeMillis() - start

                logger.info("event=transcription_success durationMs={}", duration)

                return TranscriptionResult(
                    text = text,
                    durationMs = duration
                )

            } finally {
                process?.destroyForcibly()
                readerThread?.interrupt()
                readerThread?.join(1000)
            }

        } finally {
            WhisperConcurrencyLimiter.release()
        }
    }

    private fun resolveCommand(): String {
        if (!configuredPath.isNullOrBlank()) {
            val file = File(configuredPath)
            if (file.exists()) return file.absolutePath
            logger.warn("Configured whisper not found at {}", configuredPath)
        }
        return "whisper"
    }
}