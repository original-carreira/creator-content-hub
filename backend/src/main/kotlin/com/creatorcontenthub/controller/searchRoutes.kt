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
        val limitRaw = call.request.queryParameters["limit"]
        val offsetRaw = call.request.queryParameters["offset"]

        val limit = limitRaw?.toIntOrNull()
        val offset = offsetRaw?.toIntOrNull()

        if (query.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "Query parameter 'q' is required")
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

        val result = useCase.execute(query, limit, offset)

        call.respondSuccess(result)
    }
}