package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.dto.TranscriptionResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.TranscriptionPort
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState

class TranscriptionStep(
    private val transcriptionPort: TranscriptionPort,
    private val jobRepository: JobRepository
) : PipelineStep {

    override suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState {

        // idempotência
        if (job.stage != JobStage.DOWNLOADED) {
            return job
        }

        val audioPath = requireNotNull(job.audioPath) {
            "audioPath is required for transcription step"
        }

        val result: TranscriptionResult =
            transcriptionPort.transcribe(audioPath, jobId)

        val now = System.currentTimeMillis()

        val updated = job.copy(
            stage = JobStage.TRANSCRIBED,
            transcription = result.text,
            transcriptionCompletedAt = now
        )

        jobRepository.update(jobId, updated)

        return updated
    }
}