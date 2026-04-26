package com.creatorcontenthub.infrastructure.http

import com.creatorcontenthub.application.dto.ApiError
import com.creatorcontenthub.application.dto.ApiResponse
import kotlinx.serialization.json.Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.json.encodeToJsonElement

val DurationKey = AttributeKey<Long>("Duration")

fun ApplicationCall.duration(): Long {
    return this.attributes.getOrNull(DurationKey) ?: 0L
}

inline suspend fun <reified T> ApplicationCall.respondSuccess(data: T) {
    val jsonElement = Json.encodeToJsonElement(data)

    respond(
        HttpStatusCode.OK,
        ApiResponse(
            success = true,
            data = jsonElement
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