package com.creatorcontenthub.infrastructure.http

import io.ktor.server.application.*
import org.slf4j.MDC
import java.util.*

fun Application.configureRequestId() {

    intercept(ApplicationCallPipeline.Setup) {

        val incomingRequestId = call.request.headers["X-Request-ID"]

        val requestId = if (!incomingRequestId.isNullOrBlank()) {
            try {
                UUID.fromString(incomingRequestId).toString()
            } catch (e: IllegalArgumentException) {
                UUID.randomUUID().toString()
            }
        } else {
            UUID.randomUUID().toString()
        }

        // Attach no contexto da request
        call.attributes.put(RequestIdKey, requestId)

        // MDC (logging correlation)
        MDC.put("requestId", requestId)

        // Propagar no response (ESSENCIAL)
        call.response.headers.append("X-Request-ID", requestId)

        try {
            proceed()
        } finally {
            MDC.remove("requestId")
        }
    }
}