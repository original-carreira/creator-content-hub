package com.creatorcontenthub.controller

import com.creatorcontenthub.application.usecase.ProcessTextUseCase
import com.creatorcontenthub.application.dto.ProcessTextRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.textRoutes() {

    val useCase = ProcessTextUseCase()

    post("/process") {

        val request = call.receive<ProcessTextRequest>()
        val result = useCase.execute(request.text)

        call.respond(mapOf("result" to result))
    }
}