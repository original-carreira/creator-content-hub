package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.*
import com.creatorcontenthub.domain.exception.*
import com.creatorcontenthub.domain.model.*
import com.creatorcontenthub.infrastructure.exception.ProcessExecutionException
import com.creatorcontenthub.application.service.ExistingJobResolver
import com.creatorcontenthub.application.port.JobQueueRepository
import com.creatorcontenthub.domain.model.JobQueueItem
import com.creatorcontenthub.domain.model.QueueStatus
import com.creatorcontenthub.domain.util.YoutubeUrlUtils.extractVideoId
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.IngestionMicrometerMetrics
import com.creatorcontenthub.infrastructure.storage.FileStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.postgresql.util.PSQLException
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
    private val micrometer: IngestionMicrometerMetrics,
    private val fileStorageService: FileStorageService,
    private val jobProcessor: com.creatorcontenthub.application.pipeline.JobProcessor,
    private val existingJobResolver: ExistingJobResolver,
    private val jobQueueRepository: JobQueueRepository,
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

            // CACHE CHECK SEGURO (NULL SAFE)
            if (videoId != null) {

                val existing = jobRepository.findWithIdByVideoId(videoId)

                if (existing != null) {

                    val (existingJobId, existingJob) = existing

                    val decision = existingJobResolver.resolve(existingJob)

                    logger.info(
                        "event=existing_job_detected jobId={} videoId={} existingJobId={} status={} stage={} decision={}",
                        jobId,
                        videoId,
                        existingJobId,
                        existingJob.status,
                        existingJob.stage,
                        decision
                    )

                    when (decision) {

                        ExistingJobDecision.REUSE_COMPLETED,
                        ExistingJobDecision.RETURN_PROCESSING,
                        ExistingJobDecision.ALLOW_RESUME -> {

                            return IngestYoutubeResponse(
                                jobId = existingJobId,
                                status = existingJob.status.name,
                                stage = existingJob.stage.name,
                                reused = true,
                                resumeAvailable =
                                    decision == ExistingJobDecision.ALLOW_RESUME &&
                                            existingJob.stage != JobStage.UNKNOWN &&
                                            existingJob.stage != JobStage.COMPLETED
                            )
                        }

                        ExistingJobDecision.CREATE_NEW -> {
                            logger.info(
                                "event=create_new_job_allowed videoId={}",
                                videoId
                            )
                        }
                    }
                }
            }

            val thumbnailUrl = videoId?.let {
                "https://img.youtube.com/vi/$it/hqdefault.jpg"
            }

            val initialJob = JobState.started(now, videoId).copy(
                videoId = videoId,
                thumbnailUrl = thumbnailUrl
            )

            StructuredLogger.log(
                logger,
                event = "job_created",
                jobId = jobId,
                requestId = requestId,
                status = "CREATED"
            )

            try {

                jobRepository.create(jobId, initialJob)

                metrics.incrementStarted()
                micrometer.incrementStarted()

                val queueItem = JobQueueItem(
                    jobId = jobId,
                    status = QueueStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )

                jobQueueRepository.enqueue(queueItem)

            } catch (ex: PSQLException) {

                val isUniqueViolation =
                    ex.sqlState == "23505"

                if (!isUniqueViolation || videoId == null) {
                    throw ex
                }

                logger.warn(
                    "event=concurrent_job_create_detected videoId={} jobId={}",
                    videoId,
                    jobId
                )

                val existing =
                    jobRepository.findWithIdByVideoId(videoId)

                if (existing == null) {
                    throw ex
                }

                val (existingJobId, existingJob) = existing

                logger.info(
                    "event=existing_job_recovered_after_unique_violation videoId={} existingJobId={} status={} stage={}",
                    videoId,
                    existingJobId,
                    existingJob.status,
                    existingJob.stage
                )

                return IngestYoutubeResponse(
                    jobId = existingJobId,
                    status = existingJob.status.name,
                    stage = existingJob.stage.name,
                    reused = true,
                    resumeAvailable =
                        existingJob.stage != JobStage.UNKNOWN &&
                                existingJob.stage != JobStage.COMPLETED
                )
            }

            logger.info(
                "event=job_enqueued jobId={} stage={}",
                jobId,
                JobStage.CREATED
            )

            return IngestYoutubeResponse(
                jobId = jobId,
                status = "QUEUED",
                stage = JobStage.CREATED.name,
                reused = false,
                resumeAvailable = false
            )

        } catch (ex: Exception) {
            concurrencyControl.release()
            throw ex
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