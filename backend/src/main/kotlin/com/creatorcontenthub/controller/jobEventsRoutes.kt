package com.creatorcontenthub.controller

import com.creatorcontenthub.infrastructure.runtime.RuntimeEventBus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect


fun Route.jobEventsRoutes(
    runtimeEventBus: RuntimeEventBus
) {

    get("/jobs/{jobId}/events") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                "jobId is required"
            )
            return@get
        }

        call.response.cacheControl(CacheControl.NoCache(null))
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        val events = runtimeEventBus.subscribe(jobId)

        application.log.info(
            "event=sse_client_connected jobId={}",
            jobId
        )

        val json = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        call.respondTextWriter(
            contentType = ContentType.Text.EventStream
        ) {

            suspend fun safeSseWrite(
                block: suspend () -> Unit
            ): Boolean {

                return try {

                    block()

                    true

                } catch (ex: Throwable) {

                    application.log.warn(
                        "event=sse_write_failed jobId={} message={}",
                        jobId,
                        ex.message
                    )

                    false
                }
            }

            val keepAliveInterval = 15_000L

            var lastKeepAlive = System.currentTimeMillis()

            var streamClosed = false

            try {

                events.collect { event ->

                    if (streamClosed) {
                        return@collect
                    }

                    val now = System.currentTimeMillis()

                    // ========================================
                    // KEEPALIVE
                    // ========================================

                    if (
                        now - lastKeepAlive >= keepAliveInterval
                    ) {

                        val keepAliveOk = safeSseWrite {

                            write(": ping\n\n")
                            flush()
                        }

                        if (!keepAliveOk) {

                            streamClosed = true

                            return@collect
                        }

                        application.log.debug(
                            "event=sse_keepalive_sent jobId={}",
                            jobId
                        )

                        lastKeepAlive = now
                    }

                    // ========================================
                    // EVENT WRITE
                    // ========================================

                    val writeOk = safeSseWrite {

                        write("event: ${event.event}\n")

                        val payload =
                            json.encodeToString(event)

                        write("data: $payload\n")

                        write("\n\n")

                        flush()
                    }

                    if (!writeOk) {

                        streamClosed = true

                        return@collect
                    }

                    // ========================================
                    // TERMINAL EVENTS
                    // ========================================

                    if (
                        event.event == "job_completed" ||
                        event.event == "job_failed"
                    ) {

                        safeSseWrite {

                            write("event: stream_completed\n")
                            write("data: {}\n\n")
                            flush()
                        }

                        application.log.info(
                            "event=sse_stream_closed jobId={} reason=terminal_event",
                            jobId
                        )

                        streamClosed = true

                        return@collect
                    }
                }

            } catch (ex: Throwable) {

                application.log.warn(
                    "event=sse_client_disconnected jobId={} message={}",
                    jobId,
                    ex.message
                )

            } finally {

                application.log.info(
                    "event=sse_stream_closed jobId={} reason=finally",
                    jobId
                )
            }
        }
    }
}