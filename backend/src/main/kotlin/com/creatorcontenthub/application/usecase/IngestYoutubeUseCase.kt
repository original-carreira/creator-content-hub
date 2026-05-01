package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.*
import com.creatorcontenthub.domain.exception.*
import com.creatorcontenthub.domain.model.*
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
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

        require(request.url.isNotBlank()) { "URL must not be blank" }

        val acquired = concurrencyControl.tryAcquire(acquireTimeoutMillis)

        if (!acquired) {
            metrics.incrementQueueRejections()
            throw TooManyRequestsException()
        }

        val jobId = UUID.randomUUID().toString()

        try {
            val now = System.currentTimeMillis()
            val initialJob = JobState.started(now)

            StructuredLogger.log(
                logger,
                event = "job_created",
                jobId = jobId,
                requestId = requestId,
                status = "CREATED"
            )

            jobRepository.create(jobId, initialJob)

            metrics.incrementStarted()
            micrometer.incrementStarted()

            scope.launch {
                processJob(jobId, requestId, request.url)
            }

            return IngestYoutubeResponse(jobId = jobId, status = "CREATED")

        } catch (ex: Exception) {
            concurrencyControl.release()
            throw ex
        }
    }

    private suspend fun processJob(jobId: String, requestId: String, url: String) {

        var audioPath: String? = null

        var currentJob = jobRepository.findById(jobId)
            ?: throw IllegalStateException("Job not found")

        try {

            // ---------------- DOWNLOAD ----------------
            checkCanceled(jobId)
            val downloadStart = System.currentTimeMillis()

            audioPath = videoIngestionPort.ingest(url, jobId)

            val downloadDuration = System.currentTimeMillis() - downloadStart
            recordStepSuccess("download", jobId, requestId, downloadDuration)
            metrics.recordDownloadTime(downloadDuration)
            micrometer.recordDownload(downloadDuration)

            val audioFile = validateAudioFile(audioPath)

            // ---------------- TRANSCRIPTION ----------------
            checkCanceled(jobId)
            val transcriptionStart = System.currentTimeMillis()

            val transcriptionResult = transcriptionPort.transcribe(audioFile.absolutePath, jobId)

            val transcriptionDuration = System.currentTimeMillis() - transcriptionStart
            recordStepSuccess("transcription", jobId, requestId, transcriptionDuration)
            metrics.recordTranscriptionTime(transcriptionDuration)
            micrometer.recordTranscription(transcriptionDuration)

            // ---------------- SUMMARIZATION ----------------
            val safeText = prepareTextForSummarization(transcriptionResult.text)

            checkCanceled(jobId)
            val summarizationStart = System.currentTimeMillis()

            val summaryResult = summarizationPort.summarize(safeText)

            val summarizationDuration = System.currentTimeMillis() - summarizationStart
            recordStepSuccess("summarization", jobId, requestId, summarizationDuration)
            metrics.recordSummarizationTime(summarizationDuration)
            micrometer.recordSummarization(summarizationDuration)

            // CORREÇÃO 1: extrair STRING do resultado
            val summaryText = summaryResult.summary

            val currentJob = jobRepository.findById(jobId)
                ?: throw IllegalStateException("Job not found")

            val finalState = currentJob.markDone(
                transcription = transcriptionResult.text,
                summary = summaryText,
                finishedAt = System.currentTimeMillis()
            )

            jobRepository.update(jobId, finalState)

            metrics.incrementSucceeded()
            micrometer.incrementSucceeded()

        } catch (ex: JobCanceledException) {
            // 1. Captura o timestamp exato do fim
            val finishedAt = System.currentTimeMillis()

            StructuredLogger.log(logger, "job_canceled", jobId, requestId)

            // 2. Busca o estado atual para garantir a transição
            val canceledState = currentJob.markCanceled(finishedAt)

            jobRepository.update(jobId, canceledState)

            currentJob = canceledState

            logger.info(
                "event=job_canceled_persisted jobId={} status=CANCELED finishedAt={}",
                jobId,
                finishedAt
            )

            // Interrompe o processamento da coroutine de forma limpa
            return
        }
        catch (ex: Exception) {

            val errorType = ErrorClassifier.classify(ex)

            val currentJob = jobRepository.findById(jobId)
            if (currentJob != null) {
                val failedState = currentJob.markFailed(
                    errorType = errorType,
                    errorMessage = ex.message ?: "unknown",
                    finishedAt = System.currentTimeMillis()
                )
                jobRepository.update(jobId, failedState)
            }

            metrics.incrementFailed(errorType)
            micrometer.incrementFailed(errorType)

        } finally {

            audioPath?.let {
                val file = File(it)
                if (file.exists()) file.delete()
            }

            concurrencyControl.release()
        }
    }

    private fun checkCanceled(jobId: String) {
        if (jobRepository.isCanceled(jobId)) {
            throw JobCanceledException()
        }
    }

    private fun validateAudioFile(path: String?): File {
        val file = File(path ?: throw RuntimeException("Audio path is null"))

        repeat(5) {
            if (file.exists() && file.length() > 0) return file
            Thread.sleep(300)
        }

        throw RuntimeException("Invalid audio file")
    }

    private fun prepareTextForSummarization(text: String): String {
        val maxChars = 100_000
        if (text.length <= maxChars) return text

        val cut = text.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > 0) cut.substring(0, lastSpace) else cut
    }

    private fun recordStepSuccess(step: String, jobId: String, requestId: String, duration: Long) {
        StructuredLogger.log(
            logger,
            event = step,
            jobId = jobId,
            requestId = requestId,
            durationMs = duration,
            status = "SUCCESS"
        )
    }
}