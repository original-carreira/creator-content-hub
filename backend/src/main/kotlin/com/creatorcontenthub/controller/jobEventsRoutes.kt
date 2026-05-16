package com.creatorcontenthub.controller

import com.creatorcontenthub.infrastructure.runtime.RuntimeEventBus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

        call.respondTextWriter(
            contentType = ContentType.Text.EventStream
        ) {

            coroutineScope {

                launch {

                    while (isActive) {

                        write(": ping\n\n")
                        flush()

                        delay(15_000)
                    }
                }

                events.collect { event ->

                    write("event: ${event.event}\n")

                    write(
                        "data: " +
                                """
                {
                  "jobId":"${event.jobId}",
                  "event":"${event.event}",
                  "status":"${event.status}",
                  "stage":"${event.stage}",
                  "message":"${event.message}",
                  "progress":${event.progress},
                  "timestamp":${event.timestamp}
                }
                """.trimIndent()
                    )

                    write("\n\n")

                    flush()

                    if (
                        event.event == "job_completed" ||
                        event.event == "job_failed"
                    ) {

                        write("event: stream_completed\n")
                        write("data: {}\n\n")

                        flush()

                        return@collect
                    }
                }
            }
        }
    }
}