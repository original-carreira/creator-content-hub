package com.creatorcontenthub.controller

import com.creatorcontenthub.application.usecase.SearchJobsUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*

fun Route.searchRoutes(useCase: SearchJobsUseCase) {

    get("/jobs/search") {

        val query = call.request.queryParameters["q"]
        val status = call.request.queryParameters["status"]
        val fromRaw = call.request.queryParameters["from"]
        val toRaw = call.request.queryParameters["to"]
        val limitRaw = call.request.queryParameters["limit"]
        val offsetRaw = call.request.queryParameters["offset"]
        val debug = call.request.queryParameters["debug"] == "true"

        val from = fromRaw?.toLongOrNull()
        val to = toRaw?.toLongOrNull()
        val limit = limitRaw?.toIntOrNull()
        val offset = offsetRaw?.toIntOrNull()

        if (query.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "Query parameter 'q' is required")
            return@get
        }

        val allowedStatus = setOf("PENDING", "PROCESSING", "DONE", "FAILED")

        if (status != null && status !in allowedStatus) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid status")
            return@get
        }

        if (fromRaw != null && from == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid 'from'")
            return@get
        }

        if (toRaw != null && to == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid 'to'")
            return@get
        }

        if (limitRaw != null && limit == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid limit")
            return@get
        }

        if (offsetRaw != null && offset == null) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid offset")
            return@get
        }

        if (from != null && to != null && from > to) {
            call.respondError(HttpStatusCode.BadRequest, "'from' must be <= 'to'")
            return@get
        }

        val result = useCase.execute(
            query = query,
            status = status,
            from = from,
            to = to,
            limit = limit,
            offset = offset,
            debug = debug
        )

        call.respondSuccess(result)
    }
}