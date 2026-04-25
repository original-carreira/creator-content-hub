package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.*
import com.creatorcontenthub.infrastructure.http.getPythonMetrics
import com.creatorcontenthub.infrastructure.http.getRouteMetrics
import com.creatorcontenthub.infrastructure.http.getTotalRequests
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.HikariMetrics
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.metricsRoutes(
    ingestionMetrics: IngestionMetrics,
    hikariMetrics: HikariMetrics
) {

    route("/metrics") {

        get {

            val totalRequests = getTotalRequests()
            val routes = getRouteMetrics()
            val pythonMetrics = getPythonMetrics()

            // ✅ correto
            val ingestion = ingestionMetrics.snapshot()
            val ingestionWindow = ingestionMetrics.snapshotV2()

            val (active, idle, waiting) = hikariMetrics.snapshot()

            val response = MetricsResponse(
                totalRequests = totalRequests,
                routes = routes,
                python = PythonMetricsResponse(
                    calls = pythonMetrics.calls,
                    errors = pythonMetrics.errors,
                    timeouts = pythonMetrics.timeouts
                ),
                ingestion = ingestion,
                ingestionWindow = ingestionWindow,
                hikari = HikariMetricsResponse(
                    active = active,
                    idle = idle,
                    waiting = waiting
                )
            )

            call.respond(response)
        }
    }
}