package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.ConcurrencyControlPort
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.SummarizationPort
import com.creatorcontenthub.application.port.TranscriptionPort
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.exception.TooManyRequestsException
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.domain.exception.TranscriptionTimeoutException
import com.creatorcontenthub.domain.exception.DownloadTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class IngestYoutubeUseCase(
    private val videoIngestionPort: VideoIngestionPort,
    private val transcriptionPort: TranscriptionPort,
    private val summarizationPort: SummarizationPort,
    private val jobRepository: JobRepository,
    private val metrics: IngestionMetrics,
    private val concurrencyControl: ConcurrencyControlPort,
    private val acquireTimeoutMillis: Long,
    private val scope: CoroutineScope
) {

    fun execute(request: IngestYoutubeRequest): IngestYoutubeResponse {

        require(request.url.isNotBlank()) {
            "URL must not be blank"
        }

        val acquired = concurrencyControl.tryAcquire(acquireTimeoutMillis)

        if (!acquired) {
            metrics.incrementQueueRejections()
            throw TooManyRequestsException()
        }

        val jobId = UUID.randomUUID().toString()

        try {
            jobRepository.create(jobId)
            metrics.incrementStarted()

            scope.launch {

                val jobStart = System.currentTimeMillis()
                var audioPath: String? = null

                try {
                    // 🔹 DOWNLOAD
                    val downloadStart = System.currentTimeMillis()

                    audioPath = videoIngestionPort.ingest(
                        url = request.url,
                        jobId = jobId
                    )

                    val downloadDuration = System.currentTimeMillis() - downloadStart
                    println("[media-pipeline][ingestion] jobId=$jobId duration=${downloadDuration}ms")

                    val audioFile = File(audioPath)
                    if (!audioFile.exists() || audioFile.length() == 0L) {
                        throw RuntimeException("Invalid audio file generated")
                    }

                    // 🔹 TRANSCRIÇÃO
                    val transcriptionStart = System.currentTimeMillis()

                    val transcriptionResult = transcriptionPort.transcribe(audioPath)

                    val transcriptionDuration = System.currentTimeMillis() - transcriptionStart
                    println("[media-pipeline][transcription] jobId=$jobId duration=${transcriptionDuration}ms")

                    // 🔹 TRUNCATE PARA PERSISTÊNCIA
                    val MAX_CHARS = 100_000
                    val rawText = transcriptionResult.text

                    val safeText = if (rawText.length > MAX_CHARS) {
                        val cut = rawText.take(MAX_CHARS)
                        val lastSpace = cut.lastIndexOf(' ')
                        if (lastSpace > 0) cut.substring(0, lastSpace) else cut
                    } else {
                        rawText
                    }

                    // 🔹 SUMMARIZATION
                    val summarizationStart = System.currentTimeMillis()

                    val summaryResult = try {
                        summarizationPort.summarize(rawText)
                    } catch (ex: Exception) {
                        throw RuntimeException("Summarization failed", ex)
                    }

                    val summarizationDuration = System.currentTimeMillis() - summarizationStart
                    println("[media-pipeline][summarization] jobId=$jobId duration=${summarizationDuration}ms")

                    // 🔹 FINALIZA JOB (AGORA COM SUMMARY)
                    jobRepository.markDone(
                        jobId = jobId,
                        transcription = safeText,
                        summary = summaryResult.summary,
                        summaryCompletedAt = summaryResult.generatedAt.toEpochMilli()
                    )

                    metrics.incrementSucceeded()

                } catch (t: Throwable) {

                    val message = t.message ?: "Pipeline execution failed"

                    val errorType = when (t) {
                        is TranscriptionTimeoutException,
                        is DownloadTimeoutException -> ErrorType.TIMEOUT
                        else -> ErrorType.PROCESS_ERROR
                    }

                    try {
                        jobRepository.markFailed(
                            jobId = jobId,
                            errorType = errorType,
                            errorMessage = message
                        )
                    } catch (_: Exception) {
                        println("[media-pipeline] markFailed failed jobId=$jobId")
                    }

                    metrics.incrementFailed(errorType)
                } finally {

                    // 🔹 CLEANUP GLOBAL
                    try {
                        if (audioPath != null) {
                            val file = File(audioPath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    } catch (cleanupEx: Exception) {
                        println("[media-pipeline] cleanup failed jobId=$jobId: ${cleanupEx.message}")
                    }

                    // 🔹 LATÊNCIA TOTAL
                    val totalDuration = System.currentTimeMillis() - jobStart
                    println("[media-pipeline][job] jobId=$jobId totalDuration=${totalDuration}ms")

                    if (totalDuration > 5 * 60 * 1000) {
                        println("[media-pipeline][WARN] slow job jobId=$jobId duration=${totalDuration}ms")
                    }

                    concurrencyControl.release()
                }
            }

            return IngestYoutubeResponse(
                jobId = jobId,
                status = JobStatus.PROCESSING.name
            )

        } catch (ex: Exception) {
            concurrencyControl.release()
            throw ex
        }
    }
}