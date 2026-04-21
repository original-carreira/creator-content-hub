package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.ExportTextRequest
import com.creatorcontenthub.application.usecase.ExportTextUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.exportRoutes(exportUseCase: ExportTextUseCase) {

    post("/export/txt") {

        val request = call.receive<ExportTextRequest>()

        // 🔹 Validação (mantida)
        if (request.text.isBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "text is required",
                "INVALID_REQUEST"
            )
            return@post
        }

        val result = exportUseCase.execute(request.text)

        // 🔹 Nome do arquivo (preparado para evolução futura)
        val filename = "output.txt"

        // 🔹 Header robusto (compatível com UTF-8 e browsers modernos)
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, filename)
                .withParameter("filename*", "UTF-8''$filename")
                .toString()
        )

        // 🔹 Resposta como arquivo texto
        call.respondText(
            text = result,
            contentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8)
        )
    }
}