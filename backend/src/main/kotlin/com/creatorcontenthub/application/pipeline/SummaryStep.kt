package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.dto.SummarizationResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.SummarizationPort
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.storage.FileStorageService

class SummaryStep(
    private val summarizationPort: SummarizationPort,
    private val jobRepository: JobRepository,
    private val fileStorageService: FileStorageService
) : PipelineStep {

    override val supportedStage = JobStage.TRANSCRIBED

    override suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState {

        // idempotência
        if (job.stage != JobStage.TRANSCRIBED) {
            return job
        }

        val transcription = requireNotNull(job.transcription) {
            "transcription is required for summary step"
        }

        val result: SummarizationResult =
            summarizationPort.summarize(transcription)

        val now = System.currentTimeMillis()

        val summaryPath = fileStorageService.saveSummary(
            jobId = jobId,
            content = result.summary
        )

        val updated = job.copy(
            status = JobStatus.PROCESSING,
            stage = JobStage.SUMMARIZED,
            summary = result.summary,
            summaryPath = summaryPath,
            summaryCompletedAt = now
        )

        jobRepository.update(jobId, updated)

        return updated
    }
}