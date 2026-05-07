package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.ResumeJobResponse
import com.creatorcontenthub.application.pipeline.JobProcessor
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ResumeJobUseCase(
    private val repository: JobRepository,
    private val jobProcessor: JobProcessor,
    private val scope: CoroutineScope
) {

    suspend fun execute(jobId: String): ResumeJobResponse {

        val job = repository.findById(jobId)
            ?: throw IllegalArgumentException("Job not found")

        // COMPLETED = NO-OP
        if (job.stage == JobStage.COMPLETED) {

            return ResumeJobResponse(
                jobId = jobId,
                status = job.status.name,
                stage = job.stage.name,
                resumed = false,
                message = "already completed"
            )
        }

        // UNKNOWN = inválido
        if (job.stage == JobStage.UNKNOWN) {

            return ResumeJobResponse(
                jobId = jobId,
                status = job.status.name,
                stage = job.stage.name,
                resumed = false,
                message = "unknown job stage"
            )
        }

        scope.launch {
            jobProcessor.process(
                jobId = jobId,
                initialState = job
            )
        }

        val message = when (job.stage) {

            JobStage.CREATED ->
                "resumed successfully"

            JobStage.DOWNLOADED ->
                "resumed from transcription stage"

            JobStage.TRANSCRIBED ->
                "resumed from summary stage"

            JobStage.SUMMARIZED ->
                "resumed successfully"

            JobStage.FAILED ->
                "resumed successfully"

            else ->
                "resumed successfully"
        }

        return ResumeJobResponse(
            jobId = jobId,
            status = JobStatus.PROCESSING.name,
            stage = job.stage.name,
            resumed = true,
            message = message
        )
    }
}