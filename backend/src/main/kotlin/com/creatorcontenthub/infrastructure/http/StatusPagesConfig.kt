package com.creatorcontenthub.infrastructure.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {

    val log = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {

        exception<IllegalArgumentException> { call, cause ->
            log.error("Validation error on ${call.request.path()}", cause)

            call.respondError(
                HttpStatusCode.BadRequest,
                cause.message ?: "Invalid request",
                "INVALID_REQUEST"
            )
        }

        exception<Throwable> { call, cause ->
            log.error("Unhandled error on ${call.request.path()}", cause)

            call.respondError(
                HttpStatusCode.InternalServerError,
                "Internal Server Error",
                "INTERNAL_ERROR"
            )
        }
    }
}