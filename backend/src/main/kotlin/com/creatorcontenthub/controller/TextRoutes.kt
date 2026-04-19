package com.creatorcontenthub.controller

import com.creatorcontenthub.application.usecase.ProcessTextUseCase
import com.creatorcontenthub.application.dto.ProcessTextRequest
import com.creatorcontenthub.application.dto.ProcessTextResponse
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.textRoutes() {

    val useCase = ProcessTextUseCase()

    post("/process") {

        val request = call.receive<ProcessTextRequest>()

        // ✅ VALIDAÇÃO DE INPUT
        if (request.text.isBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "text is required",
                "INVALID_REQUEST"
            )
            return@post
        }

        val result = useCase.execute(request.text)

        // ✅ RESPOSTA PADRONIZADA
        call.respondSuccess(ProcessTextResponse(result))
    }
}