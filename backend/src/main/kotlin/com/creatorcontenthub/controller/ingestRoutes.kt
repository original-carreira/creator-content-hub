package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.IngestStatusResponse
import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.usecase.CancelJobUseCase
import com.creatorcontenthub.application.usecase.IngestYoutubeUseCase
import com.creatorcontenthub.application.usecase.ListJobsUseCase
import com.creatorcontenthub.application.usecase.ResumeJobUseCase
import com.creatorcontenthub.domain.exception.TooManyRequestsException
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import com.creatorcontenthub.infrastructure.http.requestId
import com.creatorcontenthub.infrastructure.logging.StructuredLogger
import org.slf4j.LoggerFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.ingestRoutes(
    useCase: IngestYoutubeUseCase,
    jobRepository: JobRepository,
    listJobsUseCase: ListJobsUseCase,
    cancelJobUseCase: CancelJobUseCase,
    resumeJobUseCase: ResumeJobUseCase
) {
    val logger = LoggerFactory.getLogger("IngestRoutes")
    post("/ingest/youtube") {

        val request = try {
            call.receive<IngestYoutubeRequest>()
        } catch (ex: Exception) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid request body")
            return@post
        }

        if (request.url.isBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "URL must not be empty")
            return@post
        }

        try {
            val requestId = call.requestId()

            val response = useCase.execute(
                request = request,
                requestId = requestId
            )
            call.respondSuccess(response)

        } catch (ex: TooManyRequestsException) {

            call.respondError(
                HttpStatusCode.TooManyRequests,
                "System is overloaded, try again later"
            )

        } catch (ex: IllegalArgumentException) {

            call.respondError(
                HttpStatusCode.BadRequest,
                ex.message ?: "Invalid input"
            )

        } catch (ex: IllegalArgumentException) {

            call.respondError(
                HttpStatusCode.BadRequest,
                ex.message ?: "Invalid configuration"
            )

        } catch (ex: TooManyRequestsException) {

            call.respondError(
                HttpStatusCode.TooManyRequests,
                "System is overloaded, try again later"
            )

        } catch (ex: Exception) {

            ex.printStackTrace()
            call.respondError(
                HttpStatusCode.InternalServerError,
                "Failed to start ingestion"
            )
        }
    }

    get("/ingest/{jobId}") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid jobId")
            return@get
        }

        val state = jobRepository.findById(jobId)

        if (state == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val response = IngestStatusResponse(
            jobId = jobId,
            status = state.status.name,
            createdAt = state.createdAt,
            startedAt = state.startedAt,
            finishedAt = state.finishedAt,
            errorType = state.errorType?.name,
            errorMessage = state.errorMessage,
            transcription = state.transcription
        )

        call.respondSuccess(response)
    }

    get("/jobs") {

        val params = call.request.queryParameters

        val status = params["status"]
        val sort = params["sort"]
        val order = params["order"]

        val limitRaw = params["limit"]
        val offsetRaw = params["offset"]
        val fromRaw = params["from"]
        val toRaw = params["to"]

        val limit = limitRaw?.toIntOrNull()
        val offset = offsetRaw?.toIntOrNull()
        val from = fromRaw?.toLongOrNull()
        val to = toRaw?.toLongOrNull()

        // 🔒 validação antecipada (evita passar lixo pro use case)
        if (limitRaw != null && limit == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid limit")
            return@get
        }

        if (offsetRaw != null && offset == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid offset")
            return@get
        }

        if (fromRaw != null && from == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid from timestamp")
            return@get
        }

        if (toRaw != null && to == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid to timestamp")
            return@get
        }

        try {
            val result = listJobsUseCase.execute(
                status = status,
                from = from,
                to = to,
                sort = sort,
                order = order,
                limit = limit,
                offset = offset
            )
            call.respondSuccess(result)

        } catch (ex: IllegalArgumentException) {

            call.respondError(
                HttpStatusCode.BadRequest,
                ex.message ?: "Invalid query parameters"
            )

        } catch (ex: Exception) {

            call.respondError(
                HttpStatusCode.InternalServerError,
                "Failed to list jobs"
            )
        }
    }

    post("/jobs/{jobId}/cancel") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid jobId")
            return@post
        }

        try {

            val requestId = call.requestId()
            cancelJobUseCase.execute(jobId)

            StructuredLogger.log(
                logger = logger,
                event = "job_cancel_requested",
                jobId = jobId,
                requestId = requestId,
                status = "CANCELED"
            )

            call.respondSuccess(mapOf("jobId" to jobId, "status" to "CANCELED"))

        } catch (ex: IllegalArgumentException) {

            call.respondError(
                HttpStatusCode.NotFound,
                ex.message ?: "Job not found"
            )

        } catch (ex: IllegalStateException) {

            call.respondError(
                HttpStatusCode.Conflict,
                ex.message ?: "Cannot cancel job"
            )

        } catch (ex: Exception) {

            call.respondError(
                HttpStatusCode.InternalServerError,
                "Failed to cancel job"
            )
        }
    }

    post("/jobs/{jobId}/resume") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid jobId")
            return@post
        }

        try {

            val response = resumeJobUseCase.execute(jobId)

            call.respondSuccess(response)

        } catch (ex: IllegalArgumentException) {

            call.respondError(
                HttpStatusCode.NotFound,
                ex.message ?: "Job not found"
            )

        } catch (ex: Exception) {

            call.respondError(
                HttpStatusCode.InternalServerError,
                "Failed to resume job"
            )
        }
    }
}