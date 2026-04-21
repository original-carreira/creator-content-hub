package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import java.util.UUID

class IngestYoutubeUseCase(
    private val videoIngestionPort: VideoIngestionPort,
    private val jobStateStore: InMemoryJobStatusStore,
    private val metrics: IngestionMetrics
) {

    fun execute(request: IngestYoutubeRequest): IngestYoutubeResponse {

        // 1. validação
        require(request.url.isNotBlank()) {
            "URL must not be blank"
        }

        val jobId = UUID.randomUUID().toString()

        // 2. registrar estado inicial do job
        jobStateStore.create(jobId)

        // 3. agora sim: job oficialmente iniciado
        metrics.incrementStarted()

        // 4. execução assíncrona
        videoIngestionPort.ingest(
            url = request.url,
            jobId = jobId
        )

        return IngestYoutubeResponse(
            jobId = jobId,
            status = JobStatus.PROCESSING.name
        )
    }
}