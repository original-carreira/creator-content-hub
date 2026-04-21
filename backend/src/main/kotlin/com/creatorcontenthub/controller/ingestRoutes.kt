package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.IngestStatusResponse
import com.creatorcontenthub.application.dto.IngestYoutubeRequest
import com.creatorcontenthub.application.usecase.IngestYoutubeUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.ingestRoutes(
    useCase: IngestYoutubeUseCase,
    jobStatusStore: InMemoryJobStatusStore // ✅ NOVA DEPENDÊNCIA
) {

    // ==============================
    // POST /ingest/youtube (EXISTENTE)
    // ==============================
    post("/ingest/youtube") {

        val request = try {
            call.receive<IngestYoutubeRequest>()
        } catch (ex: Exception) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "Invalid request body"
            )
            return@post
        }

        if (request.url.isBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "URL must not be empty"
            )
            return@post
        }

        val response = try {
            useCase.execute(request)
        } catch (ex: IllegalArgumentException) {
            call.respondError(
                HttpStatusCode.BadRequest,
                ex.message ?: "Invalid input"
            )
            return@post
        } catch (ex: Exception) {
            call.respondError(
                HttpStatusCode.InternalServerError,
                "Failed to start ingestion"
            )
            return@post
        }

        call.respondSuccess(response)
    }

    // ==============================
    // GET /ingest/{jobId} (NOVO)
    // ==============================
    get("/ingest/{jobId}") {

        val jobId = call.parameters["jobId"]

        // ✔ validação básica
        if (jobId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "Invalid jobId"
            )
            return@get
        }

        // ✔ verificação de existência
        if (!jobStatusStore.exists(jobId)) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Job not found"
            )
            return@get
        }

        val status = jobStatusStore.get(jobId)!!

        val response = IngestStatusResponse(
            jobId = jobId,
            status = status
        )

        call.respondSuccess(response)
    }
}