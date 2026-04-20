package com.creatorcontenthub.infrastructure.http

import io.ktor.server.application.*
import org.slf4j.MDC
import java.util.*

fun Application.configureRequestId() {

    intercept(ApplicationCallPipeline.Setup) {

        val requestId = call.request.headers["X-Request-ID"]
            ?: UUID.randomUUID().toString()

        call.attributes.put(RequestIdKey, requestId)

        try {
            MDC.put("requestId", requestId)

            proceed()

        } finally {
            MDC.remove("requestId")
        }
    }
}