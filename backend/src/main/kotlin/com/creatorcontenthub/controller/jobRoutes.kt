package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.JobResponse
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.*
import io.ktor.server.application.*

fun Route.jobRoutes(repository: JobRepository) {

    get("/jobs/{jobId}") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = repository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val response = JobResponse(
            status = job.status.name,
            createdAt = job.createdAt,
            startedAt = job.startedAt,
            finishedAt = job.finishedAt,
            transcription = job.transcription,
            summary = job.summary
        )

        call.respondSuccess(response)
    }
}