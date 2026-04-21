package com.creatorcontenthub.infrastructure.http

import com.creatorcontenthub.domain.exception.TooManyRequestsException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {

    val log = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {

        exception<IllegalArgumentException> { call, cause ->

            val requestId = call.requestId()

            log.error(
                "[requestId=$requestId] Validation error on ${call.request.path()}",
                cause
            )

            call.respondError(
                HttpStatusCode.BadRequest,
                cause.message ?: "Invalid request",
                "INVALID_REQUEST"
            )
        }

        /**
         * 🆕 BACKPRESSURE — rejeição controlada
         *
         * Mapeia saturação do sistema para HTTP 429.
         *
         * IMPORTANTE:
         * - não é erro interno → usar log.warn
         * - mantém contrato de resposta consistente
         */
        exception<TooManyRequestsException> { call, cause ->

            val requestId = call.requestId()

            log.warn(
                "[requestId=$requestId] Too many requests on ${call.request.path()}",
                cause
            )

            call.respondError(
                HttpStatusCode.TooManyRequests,
                cause.message ?: "Too many requests. Please try again later.",
                "TOO_MANY_REQUESTS"
            )
        }

        exception<Throwable> { call, cause ->

            val requestId = call.requestId()

            log.error(
                "[requestId=$requestId] Unhandled error on ${call.request.path()}",
                cause
            )

            call.respondError(
                HttpStatusCode.InternalServerError,
                "Internal Server Error",
                "INTERNAL_ERROR"
            )
        }
    }
}