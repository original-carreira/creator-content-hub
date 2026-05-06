package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.*
import com.creatorcontenthub.domain.exception.*
import com.creatorcontenthub.domain.model.*
import com.creatorcontenthub.infrastructure.exception.ProcessExecutionException
import com.creatorcontenthub.application.resilience.ErrorClassifier
import com.creatorcontenthub.domain.util.YoutubeUrlUtils.extractVideoId
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.IngestionMicrometerMetrics
import com.creatorcontenthub.infrastructure.storage.FileStorageService
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
    private val micrometer: IngestionMicrometerMetrics,
    private val fileStorageService: FileStorageService
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

            val videoId = extractVideoId(request.url)

            logger.info(
                "event=video_id_extracted jobId={} videoId={}",
                jobId,
                videoId
            )

            // ✅ CACHE CHECK SEGURO (NULL SAFE)
            if (videoId != null) {
                val existing = jobRepository.findWithIdByVideoId(videoId)

                if (existing != null) {
                    val (existingJobId, existingJob) = existing

                    logger.info(
                        "event=ingest_cache_hit_pre jobId={} videoId={} existingJobId={} status={}",
                        jobId,
                        videoId,
                        existingJobId,
                        existingJob.status
                    )

                    return IngestYoutubeResponse(
                        jobId = existingJobId,
                        status = existingJob.status.name
                    )
                }
            }

            val initialJob = JobState.started(now, videoId)

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
                processJob(jobId, requestId, request.url, videoId)
            }

            return IngestYoutubeResponse(jobId = jobId, status = "CREATED")

        } catch (ex: Exception) {
            concurrencyControl.release()
            throw ex
        }
    }

    private suspend fun processJob(
        jobId: String,
        requestId: String,
        url: String,
        videoId: String?
    ) {

        var audioPath: String? = null

        val jobStartTime = System.currentTimeMillis()
        var currentStage = "unknown"

        var currentJob = jobRepository.findById(jobId)
            ?: throw IllegalStateException("Job not found: $jobId")

        currentJob = currentJob.copy(videoId = videoId)

        val thumbnailUrl = videoId?.let {
            "https://img.youtube.com/vi/$it/hqdefault.jpg"
        }

        currentJob = currentJob.copy(thumbnailUrl = thumbnailUrl)

        jobRepository.update(jobId, currentJob)

        logger.info(
            "event=thumbnail_resolved jobId={} videoId={} thumbnailUrl={}",
            jobId,
            videoId,
            thumbnailUrl
        )

        try {
            // ✅ DOUBLE-CHECK CORRETO
            if (videoId != null) {
                val existing = jobRepository.findWithIdByVideoId(videoId)

                if (existing != null) {
                    val (existingJobId, _) = existing

                    if (existingJobId != jobId) {
                        logger.info(
                            "event=ingest_cache_hit_double_check jobId={} videoId={} existingJobId={}",
                            jobId,
                            videoId,
                            existingJobId
                        )
                        return
                    }
                }
            }

            // ---------------- DOWNLOAD ----------------
            currentStage = "download"
            checkCanceled(jobId, currentStage, requestId, jobStartTime)
            logStageStart("download", jobId, requestId)

            val downloadStart = System.currentTimeMillis()

            val ingestionResult = videoIngestionPort.ingest(url, jobId)

            audioPath = ingestionResult.audioPath

            currentJob = currentJob.copy(title = ingestionResult.title)
            jobRepository.update(jobId, currentJob)

            logger.info(
                "event=job_title_persist_attempt jobId={} videoId={} title={}",
                jobId,
                currentJob.videoId,
                currentJob.title
            )

            val downloadDuration = System.currentTimeMillis() - downloadStart
            recordStepSuccess("download", jobId, requestId, downloadDuration)
            metrics.recordDownloadTime(downloadDuration)
            micrometer.recordDownload(downloadDuration)
            micrometer.recordStage("download", downloadDuration)

            val audioFile = validateAudioFile(audioPath)

            // ---------------- TRANSCRIPTION ----------------
            currentStage = "transcription"
            checkCanceled(jobId, currentStage, requestId, jobStartTime)
            logStageStart("transcription", jobId, requestId)

            val transcriptionStart = System.currentTimeMillis()

            val transcriptionResult = transcriptionPort.transcribe(audioFile.absolutePath, jobId)

            val transcriptionDuration = System.currentTimeMillis() - transcriptionStart
            recordStepSuccess("transcription", jobId, requestId, transcriptionDuration)
            metrics.recordTranscriptionTime(transcriptionDuration)
            micrometer.recordTranscription(transcriptionDuration)
            micrometer.recordStage("transcription", transcriptionDuration)

            // ---------------- SUMMARY ----------------
            val safeText = prepareTextForSummarization(transcriptionResult.text)

            currentStage = "summary"
            checkCanceled(jobId, currentStage, requestId, jobStartTime)
            logStageStart("summary", jobId, requestId)

            val summarizationStart = System.currentTimeMillis()

            val summaryResult = summarizationPort.summarize(safeText)

            val summarizationDuration = System.currentTimeMillis() - summarizationStart
            recordStepSuccess("summary", jobId, requestId, summarizationDuration)
            metrics.recordSummarizationTime(summarizationDuration)
            micrometer.recordSummarization(summarizationDuration)
            micrometer.recordStage("summary", summarizationDuration)

            // ================= FILE PERSISTENCE =================
            val transcriptionPath = fileStorageService.saveTranscription(
                jobId,
                transcriptionResult.text
            )

            val summaryPath = fileStorageService.saveSummary(
                jobId,
                summaryResult.summary
            )

            val audioStoredPath = runCatching {
                fileStorageService.saveAudio(jobId, audioPath)
            }.getOrNull()

            logger.info(
                "event=file_saved jobId={} transcriptionPath={} summaryPath={} audioPath={}",
                jobId,
                transcriptionPath,
                summaryPath,
                audioStoredPath
            )

            val finishedAt = System.currentTimeMillis()

            val finalState = currentJob.copy(
                status = JobStatus.DONE,
                transcription = transcriptionResult.text,
                summary = summaryResult.summary,
                transcriptionCompletedAt = finishedAt,
                summaryCompletedAt = finishedAt,
                finishedAt = finishedAt,
                transcriptionPath = transcriptionPath,
                summaryPath = summaryPath,
                audioPath = audioStoredPath,
                errorType = null,
                errorMessage = null
            )

            jobRepository.update(jobId, finalState)

            val totalDuration = System.currentTimeMillis() - jobStartTime

            StructuredLogger.log(
                logger,
                event = "job_completed",
                jobId = jobId,
                requestId = requestId,
                status = "SUCCESS",
                durationMs = totalDuration
            )

            micrometer.recordTotal(totalDuration)
            metrics.incrementSucceeded()
            micrometer.incrementSucceeded()

        } finally {
            audioPath?.let {
                val file = File(it)
                if (file.exists()) file.delete()
            }

            concurrencyControl.release()
        }
    }

    private fun checkCanceled(jobId: String, stage: String, requestId: String, jobStartTime: Long) {
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

    private fun recordStepSuccess(stage: String, jobId: String, requestId: String, duration: Long) {
        StructuredLogger.log(
            logger = logger,
            event = "stage_end",
            jobId = jobId,
            requestId = requestId,
            status = "SUCCESS",
            durationMs = duration,
            extra = mapOf("stage" to stage)
        )
    }

    private fun logStageStart(stage: String, jobId: String, requestId: String) {
        StructuredLogger.log(
            logger = logger,
            event = "stage_start",
            jobId = jobId,
            requestId = requestId,
            status = "RUNNING",
            extra = mapOf("stage" to stage)
        )
    }

    private fun unwrap(e: Throwable): Throwable {
        var current = e
        while (current.cause != null) current = current.cause!!
        return current
    }

    private fun extractExitCode(e: Throwable): Int? {
        return if (e is ProcessExecutionException) e.exitCode else null
    }
}