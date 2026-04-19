package com.creatorcontenthub.infrastructure.http

import com.creatorcontenthub.application.dto.ApiError
import com.creatorcontenthub.application.dto.ApiResponse
import com.creatorcontenthub.application.dto.ProcessTextResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.respondSuccess(data: ProcessTextResponse) {
    respond(
        HttpStatusCode.OK,
        ApiResponse(
            success = true,
            data = data
        )
    )
}

suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
    code: String? = null
) {
    respond(
        status,
        ApiResponse(
            success = false,
            error = ApiError(
                message = message,
                code = code
            )
        )
    )
}