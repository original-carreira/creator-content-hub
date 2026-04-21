package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.dto.IngestYoutubeResponse
import com.creatorcontenthub.application.port.ConcurrencyControlPort
import com.creatorcontenthub.application.port.VideoIngestionPort
import com.creatorcontenthub.domain.exception.TooManyRequestsException
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class IngestYoutubeUseCase(
    private val videoIngestionPort: VideoIngestionPort,
    private val jobStateStore: InMemoryJobStatusStore,
    private val metrics: IngestionMetrics,
    private val concurrencyControl: ConcurrencyControlPort,
    private val acquireTimeoutMillis: Long,
    private val scope: CoroutineScope
) {

    fun execute(request: IngestYoutubeRequest): IngestYoutubeResponse {

        require(request.url.isNotBlank()) {
            "URL must not be blank"
        }

        // 🔴 BACKPRESSURE (agora no UseCase)
        val acquired = concurrencyControl.tryAcquire(acquireTimeoutMillis)

        if (!acquired) {
            metrics.incrementQueueRejections()
            throw TooManyRequestsException()
        }

        val jobId = UUID.randomUUID().toString()

        try {
            // estado inicial
            jobStateStore.create(jobId)

            // métricas após acquire
            metrics.incrementStarted()

            // execução async
            scope.launch {
                try {
                    videoIngestionPort.ingest(
                        url = request.url,
                        jobId = jobId
                    )

                    metrics.incrementSucceeded()

                } catch (ex: Exception) {

                    val message = ex.message ?: "Ingestion execution failed"

                    jobStateStore.markFailed(
                        jobId,
                        ErrorType.UNKNOWN,
                        message
                    )

                    metrics.incrementFailed(ErrorType.UNKNOWN)

                } finally {
                    // 🔴 release correto (fim real)
                    concurrencyControl.release()
                }
            }

            return IngestYoutubeResponse(
                jobId = jobId,
                status = JobStatus.PROCESSING.name
            )

        } catch (ex: Exception) {
            // 🔴 proteção contra erro antes do launch
            concurrencyControl.release()
            throw ex
        }
    }
}