package com.creatorcontenthub.infrastructure.http

import com.creatorcontenthub.application.dto.ApiError
import com.creatorcontenthub.application.dto.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*

val DurationKey = AttributeKey<Long>("Duration")

fun ApplicationCall.duration(): Long {
    return this.attributes.getOrNull(DurationKey) ?: 0L
}

inline suspend fun <reified T> ApplicationCall.respondSuccess(data: T) {
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
        ApiResponse<Nothing>(
            success = false,
            error = ApiError(
                message = message,
                code = code
            )
        )
    )
}