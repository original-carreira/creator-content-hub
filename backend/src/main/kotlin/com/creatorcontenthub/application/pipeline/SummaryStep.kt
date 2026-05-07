package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.dto.SummarizationResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.SummarizationPort
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus

class SummaryStep(
    private val summarizationPort: SummarizationPort,
    private val jobRepository: JobRepository
) : PipelineStep {

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

        val updated = job.copy(
            status = JobStatus.DONE,
            stage = JobStage.COMPLETED,
            summary = result.summary,
            summaryCompletedAt = now,
            finishedAt = now
        )

        jobRepository.update(jobId, updated)

        return updated
    }
}