package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.dto.TranscriptionResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.TranscriptionPort
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.infrastructure.storage.FileStorageService

class TranscriptionStep(
    private val transcriptionPort: TranscriptionPort,
    private val jobRepository: JobRepository,
    private val fileStorageService: FileStorageService
) : PipelineStep {

    override val supportedStage = JobStage.AUDIO_GENERATED

    override suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState {

        // idempotência
        if (job.stage != JobStage.AUDIO_GENERATED) {
            return job
        }

        val audioPath = requireNotNull(job.audioPath) {
            "audioPath is required for transcription step"
        }

        val result: TranscriptionResult =
            transcriptionPort.transcribe(audioPath, jobId)

        val now = System.currentTimeMillis()

        val transcriptionPath = fileStorageService.saveTranscription(
            jobId = jobId,
            content = result.text
        )

        val updated = job.copy(
            stage = JobStage.TRANSCRIBED,
            transcription = result.text,
            transcriptionPath = transcriptionPath,
            transcriptionCompletedAt = now
        )

        jobRepository.update(jobId, updated)

        return updated
    }
}