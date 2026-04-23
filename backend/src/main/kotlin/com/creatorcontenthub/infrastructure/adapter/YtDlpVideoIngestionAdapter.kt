package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.exception.DownloadTimeoutException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter : VideoIngestionPort {

    companion object {
        private const val MAX_OUTPUT_LINES = 200
        private const val TIMEOUT_MINUTES = 10L
    }

    override fun ingest(url: String, jobId: String): String {

        val outputDir = File("/tmp/creator-content-hub")

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw RuntimeException("Failed to create output directory")
        }

        val outputPath = "${outputDir.absolutePath}/$jobId.mp3"

        val process = ProcessBuilder(
            "yt-dlp",
            "-x",
            "--audio-format", "mp3",
            "-o", outputPath,
            url
        )
            .redirectErrorStream(true)
            .start()

        println("yt-dlp download started (jobId=$jobId)")

        val outputLines = mutableListOf<String>()

        // 🔥 leitura em thread separada (evita deadlock)
        val readerThread = Thread {
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
        }

        readerThread.start()

        val finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)

        if (!finished) {
            process.destroyForcibly()
            readerThread.join()
            throw DownloadTimeoutException("yt-dlp timeout after $TIMEOUT_MINUTES minutes")
        }

        readerThread.join()

        val exitCode = process.exitValue()

        if (exitCode != 0) {
            val sanitized = outputLines.joinToString("\n").take(300)
            throw RuntimeException("yt-dlp failed (code=$exitCode): $sanitized")
        }

        val outputFile = File(outputPath)

        if (!outputFile.exists()) {
            throw RuntimeException("Audio file not generated at $outputPath")
        }

        println("yt-dlp download completed (jobId=$jobId)")

        return outputPath
    }
}