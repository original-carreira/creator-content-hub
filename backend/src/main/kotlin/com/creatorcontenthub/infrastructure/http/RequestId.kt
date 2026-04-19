package com.creatorcontenthub.infrastructure.http

import io.ktor.server.application.*
import io.ktor.util.*

val RequestIdKey = AttributeKey<String>("RequestId")

fun ApplicationCall.requestId(): String =
    attributes.getOrNull(RequestIdKey) ?: "unknown"