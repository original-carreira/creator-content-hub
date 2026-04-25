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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import org.slf4j.LoggerFactory

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

    private val logger = LoggerFactory.getLogger(IngestYoutubeUseCase::class.java)

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

            logger.info("[job] jobId={} status=CREATED", jobId)

            jobRepository.create(jobId, currentJob)
            metrics.incrementStarted()

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
                        logger.error(
                            "[download] jobId={} status=FAILED errorType={} message={}",
                            jobId,
                            ex::class.simpleName,
                            ex.message
                        )
                        throw ex
                    }

                    val downloadDuration = System.currentTimeMillis() - downloadStart

                    logger.info(
                        "[download] jobId={} status=SUCCESS duration={}ms",
                        jobId,
                        downloadDuration
                    )

                    // ✅ MÉTRICA
                    metrics.recordDownloadTime(downloadDuration)

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
                        logger.error(
                            "[transcription] jobId={} status=FAILED errorType={} message={}",
                            jobId,
                            ex::class.simpleName,
                            ex.message
                        )
                        throw ex
                    }

                    val transcriptionDuration = System.currentTimeMillis() - transcriptionStart

                    logger.info(
                        "[transcription] jobId={} status=SUCCESS duration={}ms",
                        jobId,
                        transcriptionDuration
                    )

                    // ✅ MÉTRICA
                    metrics.recordTranscriptionTime(transcriptionDuration)

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
                        logger.error(
                            "[summarization] jobId={} status=FAILED errorType={} message={}",
                            jobId,
                            ex::class.simpleName,
                            ex.message
                        )
                        throw RuntimeException("Summarization failed", ex)
                    }

                    val summarizationDuration = System.currentTimeMillis() - summarizationStart

                    logger.info(
                        "[summarization] jobId={} status=SUCCESS duration={}ms",
                        jobId,
                        summarizationDuration
                    )

                    // ✅ MÉTRICA
                    metrics.recordSummarizationTime(summarizationDuration)

                    // ---------------- DONE ----------------
                    val finishedAt = System.currentTimeMillis()

                    currentJob = currentJob.markDone(
                        transcription = safeText,
                        summary = summaryResult.summary,
                        finishedAt = finishedAt
                    )

                    jobRepository.update(jobId, currentJob)
                    metrics.incrementSucceeded()

                } catch (t: Throwable) {

                    val message = t.message ?: "Pipeline execution failed"

                    val errorType = when (t) {
                        is TranscriptionTimeoutException,
                        is DownloadTimeoutException -> ErrorType.TIMEOUT
                        else -> ErrorType.PROCESS_ERROR
                    }

                    logger.error(
                        "[job] jobId={} status=FAILED errorType={} message={}",
                        jobId,
                        errorType,
                        message
                    )

                    try {
                        currentJob = currentJob.markFailed(
                            errorType = errorType,
                            errorMessage = message,
                            finishedAt = System.currentTimeMillis()
                        )

                        jobRepository.update(jobId, currentJob)

                    } catch (_: Exception) {
                        logger.error(
                            "[job-update] jobId={} status=FAILED message=markFailed failed",
                            jobId
                        )
                    }

                    metrics.incrementFailed(errorType)

                } finally {

                    try {
                        if (audioPath != null) {
                            val file = File(audioPath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    } catch (cleanupEx: Exception) {
                        logger.warn(
                            "[cleanup] jobId={} status=FAILED message={}",
                            jobId,
                            cleanupEx.message
                        )
                    }

                    val totalDuration = System.currentTimeMillis() - jobStart

                    metrics.addProcessingTime(totalDuration)

                    logger.info(
                        "[job] jobId={} status={} totalDuration={}ms",
                        jobId,
                        currentJob.status,
                        totalDuration
                    )

                    if (totalDuration > 5 * 60 * 1000) {
                        logger.warn(
                            "[job] jobId={} status=SLOW totalDuration={}ms",
                            jobId,
                            totalDuration
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