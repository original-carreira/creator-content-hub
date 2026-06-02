package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.dto.IngestionResult
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState

class DownloadStep(
    private val ingestionPort: VideoIngestionPort,
    private val jobRepository: JobRepository
) : PipelineStep {

    override val supportedStage = JobStage.CREATED

    override suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState {

        // idempotência
        if (job.stage != JobStage.CREATED) {
            return job
        }

        val videoId = requireNotNull(job.videoId) {
            "videoId is required for download step"
        }

        val url = "https://www.youtube.com/watch?v=$videoId"

        val result: IngestionResult =
            ingestionPort.ingest(url, jobId)

        val updated = job.copy(
            stage = JobStage.DOWNLOADED,
            videoPath = result.videoPath,
            title = result.title
        )

        jobRepository.update(jobId, updated)

        return updated
    }
}