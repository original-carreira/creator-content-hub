package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.IngestStatusResponse
import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.usecase.IngestYoutubeUseCase
import com.creatorcontenthub.domain.exception.TooManyRequestsException
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.ingestRoutes(
    useCase: IngestYoutubeUseCase,
    jobStatusStore: InMemoryJobStatusStore
) {

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
            val response = useCase.execute(request)
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

        } catch (ex: Exception) {

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

        val state = jobStatusStore.get(jobId)

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
            errorMessage = state.errorMessage
        )

        call.respondSuccess(response)
    }
}