package com.creatorcontenthub.infrastructure.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object StructuredLogger {

    fun logger(clazz: Class<*>): Logger =
        LoggerFactory.getLogger(clazz)

    fun log(
        logger: Logger,
        event: String,
        jobId: String,
        requestId: String,
        status: String? = null,
        durationMs: Long? = null,
        errorType: String? = null,
        extra: Map<String, Any?> = emptyMap()
    ) {

        val base = mutableMapOf<String, Any?>(
            "event" to event,
            "jobId" to jobId,
            "requestId" to requestId
        )

        status?.let { base["status"] = it }
        durationMs?.let { base["duration"] = it }
        errorType?.let { base["errorType"] = it }

        base.putAll(extra)

        val message = base.entries.joinToString(" ") {
            "${it.key}=${it.value}"
        }

        logger.info(message)
    }
}