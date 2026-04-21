package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import java.util.UUID

class IngestYoutubeUseCase(
    private val videoIngestionPort: VideoIngestionPort,
    private val jobStatusStore: InMemoryJobStatusStore // ✅ NOVA DEPENDÊNCIA
) {

    fun execute(request: IngestYoutubeRequest): IngestYoutubeResponse {

        // ✔ Validação defensiva (mantida)
        require(request.url.isNotBlank()) {
            "URL must not be blank"
        }

        val jobId = UUID.randomUUID().toString()

        // ✅ REGISTRA STATUS INICIAL NO STORE
        jobStatusStore.create(jobId)

        // ✔ Execução assíncrona continua igual (sem regressão)
        videoIngestionPort.ingest(
            url = request.url,
            jobId = jobId
        )

        return IngestYoutubeResponse(
            jobId = jobId,
            status = JobStatus.PROCESSING.name // ✅ agora consistente com enum
        )
    }
}