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
        private const val OUTPUT_DIR = "/data"
    }

    override fun ingest(url: String, jobId: String): String {

        val outputDir = File(OUTPUT_DIR)

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw RuntimeException("Failed to create output directory: $OUTPUT_DIR")
        }

        // 🔥 usa ID (estável) e diretório com permissão
        val outputPathTemplate = "$OUTPUT_DIR/$jobId.%(ext)s"

        val process = ProcessBuilder(
            "yt-dlp",
            "-x",
            "--audio-format", "mp3",

            // 🔥 melhora compatibilidade com YouTube
            "--extractor-args", "youtube:player_client=android",

            // 🔥 evita problemas com nome de arquivo
            "--restrict-filenames",

            "-o", outputPathTemplate,
            url
        )
            .redirectErrorStream(true)
            .start()

        println("yt-dlp download started (jobId=$jobId)")

        val outputLines = mutableListOf<String>()

        // leitura em thread separada (evita deadlock)
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
            val sanitized = outputLines.joinToString("\n").take(500)
            throw RuntimeException("yt-dlp failed (code=$exitCode): $sanitized")
        }

        // 🔥 yt-dlp gera .mp3 após conversão
        val outputFile = File("$OUTPUT_DIR/$jobId.mp3")

        if (!outputFile.exists()) {
            throw RuntimeException("Audio file not generated at ${outputFile.absolutePath}")
        }

        println("yt-dlp download completed (jobId=$jobId)")

        return outputFile.absolutePath
    }
}