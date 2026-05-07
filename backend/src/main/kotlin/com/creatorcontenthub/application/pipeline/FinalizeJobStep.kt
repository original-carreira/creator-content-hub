package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus

class FinalizeJobStep(
    private val jobRepository: JobRepository
) : PipelineStep {

    override val supportedStage = JobStage.SUMMARIZED

    override suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState {

        // idempotência
        if (job.stage != JobStage.SUMMARIZED) {
            return job
        }

        val now = System.currentTimeMillis()

        val updated = job.copy(
            status = JobStatus.DONE,
            stage = JobStage.COMPLETED,
            finishedAt = now,
            errorType = null,
            errorMessage = null
        )

        jobRepository.update(jobId, updated)

        return updated
    }
}