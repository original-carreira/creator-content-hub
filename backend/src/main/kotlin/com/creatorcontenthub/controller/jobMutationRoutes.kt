package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.DeleteJobsRequest
import com.creatorcontenthub.application.usecase.DeleteJobUseCase
import com.creatorcontenthub.application.usecase.DeleteJobsUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

fun Route.jobMutationRoutes(
    deleteJobUseCase: DeleteJobUseCase,
    deleteJobsUseCase: DeleteJobsUseCase
) {

    // 🔹 DELETE INDIVIDUAL
    delete("/jobs/{jobId}") {
        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@delete
        }

        deleteJobUseCase.execute(jobId)

        call.respondSuccess(mapOf("deleted" to jobId))
    }

    // 🔹 DELETE MÚLTIPLOS
    post("/jobs/delete") {
        val request = call.receive<DeleteJobsRequest>()

        if (request.jobIds.isEmpty()) {
            call.respondError(HttpStatusCode.BadRequest, "jobIds cannot be empty")
            return@post
        }

        deleteJobsUseCase.execute(request.jobIds)

        call.respondSuccess(mapOf("deletedCount" to request.jobIds.size))
    }
}