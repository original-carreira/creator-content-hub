package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.exception.ExternalServiceException
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class YtDlpVideoIngestionAdapter(
    private val executor: ExecutorService,
    private val jobStatusStore: InMemoryJobStatusStore // ✅ NOVA DEPENDÊNCIA
) : VideoIngestionPort {

    override fun ingest(url: String, jobId: String) {

        executor.submit {

            val startTime = System.currentTimeMillis()

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

            try {
                println("yt-dlp download started (jobId=$jobId)")

                val process = processBuilder.start()

                val output = process.inputStream.bufferedReader().readText()

                val finished = process.waitFor(10, TimeUnit.MINUTES)

                if (!finished) {
                    process.destroy()

                    // ✅ ATUALIZA STATUS COMO FAILED
                    jobStatusStore.update(jobId, JobStatus.FAILED)

                    throw ExternalServiceException("yt-dlp timeout (jobId=$jobId)")
                }

                val exitCode = process.exitValue()

                if (exitCode != 0) {

                    // ✅ ATUALIZA STATUS COMO FAILED
                    jobStatusStore.update(jobId, JobStatus.FAILED)

                    throw ExternalServiceException(
                        "yt-dlp failed (jobId=$jobId, stderr=$output)"
                    )
                }

                val duration = System.currentTimeMillis() - startTime

                println("yt-dlp download completed (jobId=$jobId, duration=${duration}ms)")

                // ✅ SUCESSO
                jobStatusStore.update(jobId, JobStatus.DONE)

            } catch (ex: Exception) {

                println("yt-dlp failed (jobId=$jobId, error=${ex.message})")

                // ✅ GARANTE FAILED MESMO EM ERRO INESPERADO
                jobStatusStore.update(jobId, JobStatus.FAILED)

                // ⚠️ mantém comportamento original (sem regressão)
                throw ExternalServiceException("yt-dlp execution error", ex)
            }
        }
    }
}