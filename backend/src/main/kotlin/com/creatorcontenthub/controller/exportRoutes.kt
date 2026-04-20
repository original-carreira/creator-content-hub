package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.ExportTextRequest
import com.creatorcontenthub.application.dto.ExportTextResponse
import com.creatorcontenthub.application.usecase.ExportTextUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Route.exportRoutes(exportUseCase: ExportTextUseCase) {

    post("/export/txt") {

        val request = call.receive<ExportTextRequest>()

        // 🔥 ALINHADO COM /process (consistência de API)
        if (request.text.isBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "text is required",
                "INVALID_REQUEST"
            )
            return@post
        }

        val result = exportUseCase.execute(request.text)

        call.respondSuccess(
            ExportTextResponse(result)
        )
    }
}