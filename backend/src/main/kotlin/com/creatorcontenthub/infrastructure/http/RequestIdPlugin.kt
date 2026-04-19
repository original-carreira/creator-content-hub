package com.creatorcontenthub.infrastructure.http

import io.ktor.server.application.*
import io.ktor.util.*
import java.util.*

fun Application.configureRequestId() {

    intercept(ApplicationCallPipeline.Setup) {
        val requestId = UUID.randomUUID().toString()

        call.attributes.put(RequestIdKey, requestId)

        proceed()
    }
}