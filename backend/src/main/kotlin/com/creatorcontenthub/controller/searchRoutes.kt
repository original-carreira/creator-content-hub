package com.creatorcontenthub.controller

import com.creatorcontenthub.application.usecase.SearchJobsUseCase
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*

fun Route.searchRoutes(useCase: SearchJobsUseCase) {

    get("/jobs/search") {

        val query = call.request.queryParameters["q"]
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()
        val offset = call.request.queryParameters["offset"]?.toIntOrNull()

        val result = useCase.execute(query, limit, offset)

        call.respond(result)
    }
}