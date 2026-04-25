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
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.ErrorClassifier
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.metrics.IngestionMicrometerMetrics
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
    private val scope: CoroutineScope,
    private val micrometer: IngestionMicrometerMetrics
) {

    private val logger = StructuredLogger.logger(javaClass)

    fun execute(request: IngestYoutubeRequest, requestId: String): IngestYoutubeResponse {

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
            val now = System.currentTimeMillis()

            var currentJob = JobState(
                status = JobStatus.PROCESSING,
                createdAt = now,
                startedAt = now,
                finishedAt = null,
                transcription = null,
                transcriptionCompletedAt = null,
                summary = null,
                summaryCompletedAt = null,
                errorType = null,
                errorMessage = null
            )

            StructuredLogger.log(
                logger,
                event = "job_created",
                jobId = jobId,
                requestId = requestId,
                status = "CREATED"
            )

            jobRepository.create(jobId, currentJob)

            metrics.incrementStarted()
            micrometer.incrementStarted()

            scope.launch {

                val jobStart = System.currentTimeMillis()
                var audioPath: String? = null

                try {
                    // ---------------- DOWNLOAD ----------------
                    val downloadStart = System.currentTimeMillis()

                    audioPath = try {
                        videoIngestionPort.ingest(
                            url = request.url,
                            jobId = jobId
                        )
                    } catch (ex: Exception) {
                        StructuredLogger.log(
                            logger,
                            event = "download",
                            jobId = jobId,
                            requestId = requestId,
                            status = "FAILED",
                            errorType = ex::class.simpleName
                        )
                        throw ex
                    }

                    val downloadDuration = System.currentTimeMillis() - downloadStart

                    StructuredLogger.log(
                        logger,
                        event = "download",
                        jobId = jobId,
                        requestId = requestId,
                        durationMs = downloadDuration,
                        status = "SUCCESS"
                    )

                    metrics.recordDownloadTime(downloadDuration)
                    micrometer.recordDownload(downloadDuration)

                    val audioFile = File(audioPath)
                    if (!audioFile.exists() || audioFile.length() == 0L) {
                        throw RuntimeException("Invalid audio file generated")
                    }

                    // ---------------- TRANSCRIPTION ----------------
                    val transcriptionStart = System.currentTimeMillis()

                    val safeAudioPath = audioPath
                        ?: throw IllegalStateException("audioPath is null after download")

                    val transcriptionResult = try {
                        transcriptionPort.transcribe(safeAudioPath)
                    } catch (ex: Exception) {
                        StructuredLogger.log(
                            logger,
                            event = "transcription",
                            jobId = jobId,
                            requestId = requestId,
                            status = "FAILED",
                            errorType = ex::class.simpleName
                        )
                        throw ex
                    }

                    val transcriptionDuration = System.currentTimeMillis() - transcriptionStart

                    StructuredLogger.log(
                        logger,
                        event = "transcription",
                        jobId = jobId,
                        requestId = requestId,
                        durationMs = transcriptionDuration,
                        status = "SUCCESS"
                    )

                    metrics.recordTranscriptionTime(transcriptionDuration)
                    micrometer.recordTranscription(transcriptionDuration)

                    val MAX_CHARS = 100_000
                    val rawText = transcriptionResult.text

                    val safeText = if (rawText.length > MAX_CHARS) {
                        val cut = rawText.take(MAX_CHARS)
                        val lastSpace = cut.lastIndexOf(' ')
                        if (lastSpace > 0) cut.substring(0, lastSpace) else cut
                    } else {
                        rawText
                    }

                    // ---------------- SUMMARIZATION ----------------
                    val summarizationStart = System.currentTimeMillis()

                    val summaryResult = try {
                        summarizationPort.summarize(rawText)
                    } catch (ex: Exception) {
                        StructuredLogger.log(
                            logger,
                            event = "summarization",
                            jobId = jobId,
                            requestId = requestId,
                            status = "FAILED",
                            errorType = ex::class.simpleName
                        )
                        throw RuntimeException("Summarization failed", ex)
                    }

                    val summarizationDuration = System.currentTimeMillis() - summarizationStart

                    StructuredLogger.log(
                        logger,
                        event = "summarization",
                        jobId = jobId,
                        requestId = requestId,
                        durationMs = summarizationDuration,
                        status = "SUCCESS"
                    )

                    metrics.recordSummarizationTime(summarizationDuration)
                    micrometer.recordSummarization(summarizationDuration)

                    // ---------------- DONE ----------------
                    val finishedAt = System.currentTimeMillis()

                    currentJob = currentJob.markDone(
                        transcription = safeText,
                        summary = summaryResult.summary,
                        finishedAt = finishedAt
                    )

                    jobRepository.update(jobId, currentJob)

                    metrics.incrementSucceeded()
                    micrometer.incrementSucceeded()

                } catch (t: Throwable) {

                    val message = t.message ?: "Pipeline execution failed"

                    val errorType = ErrorClassifier.classify(throwable = t)

                    // 🆕 MÉTRICA DE RETRY (resiliência)
                    if (errorType == ErrorType.TIMEOUT || errorType == ErrorType.DEPENDENCY_FAILURE) {

                        val stage = when {
                            t is DownloadTimeoutException -> "download"
                            t is TranscriptionTimeoutException -> "transcription"
                            else -> "unknown"
                        }

                        metrics.incrementRetry(stage)
                    }

                    StructuredLogger.log(
                        logger,
                        event = "job_failed",
                        jobId = jobId,
                        requestId = requestId,
                        status = "FAILED",
                        errorType = errorType.name
                    )

                    try {
                        currentJob = currentJob.markFailed(
                            errorType = errorType,
                            errorMessage = message,
                            finishedAt = System.currentTimeMillis()
                        )

                        jobRepository.update(jobId, currentJob)

                    } catch (_: Exception) {
                        StructuredLogger.log(
                            logger,
                            event = "job_update",
                            jobId = jobId,
                            requestId = requestId,
                            status = "FAILED",
                            errorType = "markFailed_error"
                        )
                    }

                    metrics.incrementFailed(errorType)
                    micrometer.incrementFailed(errorType.name)

                } finally {

                    try {
                        if (audioPath != null) {
                            val file = File(audioPath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    } catch (cleanupEx: Exception) {
                        StructuredLogger.log(
                            logger,
                            event = "cleanup",
                            jobId = jobId,
                            requestId = requestId,
                            status = "FAILED",
                            errorType = cleanupEx::class.simpleName
                        )
                    }

                    val totalDuration = System.currentTimeMillis() - jobStart

                    metrics.addProcessingTime(totalDuration)
                    micrometer.recordTotal(totalDuration)

                    StructuredLogger.log(
                        logger,
                        event = "job_complete",
                        jobId = jobId,
                        requestId = requestId,
                        status = currentJob.status.name,
                        durationMs = totalDuration
                    )

                    if (totalDuration > 5 * 60 * 1000) {
                        StructuredLogger.log(
                            logger,
                            event = "job_slow",
                            jobId = jobId,
                            requestId = requestId,
                            durationMs = totalDuration,
                            status = "SLOW"
                        )
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