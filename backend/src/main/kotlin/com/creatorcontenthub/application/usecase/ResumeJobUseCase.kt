package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.ResumeJobResponse
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobStatus

class ResumeJobUseCase(
    private val repository: JobRepository
) {

    fun execute(jobId: String): ResumeJobResponse {

        val job = repository.findById(jobId)
            ?: throw IllegalArgumentException("Job not found")

        return when (job.stage) {

            JobStage.COMPLETED -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = false,
                    message = "Job already completed"
                )
            }

            JobStage.TRANSCRIBED -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = true,
                    message = "Ready to resume from summary stage"
                )
            }

            JobStage.DOWNLOADED -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = true,
                    message = "Ready to resume from transcription stage"
                )
            }

            JobStage.CREATED -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = true,
                    message = "Ready to resume from download stage"
                )
            }

            JobStage.FAILED -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = true,
                    message = "Job failed previously and can be resumed"
                )
            }

            JobStage.SUMMARIZED -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = true,
                    message = "Ready to finalize completed job"
                )
            }

            JobStage.UNKNOWN -> {
                ResumeJobResponse(
                    jobId = jobId,
                    status = job.status.name,
                    stage = job.stage.name,
                    resumed = false,
                    message = "Unknown job stage"
                )
            }
        }
    }
}