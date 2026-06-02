package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.port.AudioGenerationPort
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState

class GenerateAudioStep(
    private val audioGenerationPort: AudioGenerationPort,
    private val jobRepository: JobRepository
) : PipelineStep {

    override val supportedStage = JobStage.DOWNLOADED

    override suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState {

        // idempotência
        if (job.stage != JobStage.DOWNLOADED) {
            return job
        }

        val videoPath = requireNotNull(job.videoPath) {
            "videoPath is required for audio generation step"
        }

        val audioPath = audioGenerationPort.generateAudio(
            videoPath = videoPath,
            jobId = jobId
        )

        val updated = job.copy(
            stage = JobStage.AUDIO_GENERATED,
            audioPath = audioPath
        )

        jobRepository.update(
            jobId,
            updated
        )

        return updated
    }
}