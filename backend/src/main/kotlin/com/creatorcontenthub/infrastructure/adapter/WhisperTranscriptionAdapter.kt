package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.TranscriptionResult
import com.creatorcontenthub.application.port.TranscriptionPort
import com.creatorcontenthub.domain.exception.TranscriptionTimeoutException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class WhisperTranscriptionAdapter(
    private val whisperCommand: String = "whisper",
    private val timeoutMinutes: Long = 10
) : TranscriptionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 300
    }

    override fun transcribe(audioPath: String): TranscriptionResult {

        val start = System.currentTimeMillis()

        val audioFile = File(audioPath)

        if (!audioFile.exists()) {
            throw RuntimeException("Audio file not found: $audioPath")
        }

        val outputDir = audioFile.parentFile

        val process = ProcessBuilder(
            whisperCommand,
            audioPath,
            "--output_format", "txt",
            "--output_dir", outputDir.absolutePath
        )
            .redirectErrorStream(true)
            .start()

        val outputLines = mutableListOf<String>()

        // 🔥 CONSUMO DE STREAM (evita deadlock)
        val readerThread = Thread {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                var count = 0

                while (reader.readLine().also { line = it } != null) {
                    if (count < MAX_OUTPUT_LINES) {
                        outputLines.add(line!!)
                    }
                    count++
                }
            }
        }

        readerThread.start()

        val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

        if (!finished) {
            process.destroyForcibly()
            readerThread.join()
            throw TranscriptionTimeoutException("Transcription timeout after $timeoutMinutes minutes")
        }

        readerThread.join(5000)

        if (readerThread.isAlive) {
            readerThread.interrupt()
        }

        val exitCode = process.exitValue()

        if (exitCode != 0) {
            val error = outputLines.joinToString("\n").take(500)
            throw RuntimeException("Whisper failed (code=$exitCode): $error")
        }

        // 🔥 DETECÇÃO SEGURA DO OUTPUT
        val candidates = outputDir.listFiles { _, name ->
            name.endsWith(".txt") && name.startsWith(audioFile.nameWithoutExtension)
        } ?: emptyArray()

        val expectedFile = candidates.maxByOrNull { it.lastModified() }
            ?: throw RuntimeException("Transcription output not found in ${outputDir.absolutePath}")

        if (!expectedFile.exists()) {
            throw RuntimeException("Transcription output not found: ${expectedFile.absolutePath}")
        }

        val text = expectedFile.readText().trim()

        return TranscriptionResult(
            text = text,
            durationMs = System.currentTimeMillis() - start
        )
    }
}