package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.MetricsResponse
import com.creatorcontenthub.application.dto.PythonMetricsResponse
import com.creatorcontenthub.infrastructure.http.getPythonMetrics
import com.creatorcontenthub.infrastructure.http.getRouteMetrics
import com.creatorcontenthub.infrastructure.http.getTotalRequests
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.metricsRoutes() {

    route("/metrics") {

        get {
            val totalRequests = getTotalRequests()
            val routes = getRouteMetrics()
            val pythonMetrics = getPythonMetrics()

            val response = MetricsResponse(
                totalRequests = totalRequests,
                routes = routes,
                python = PythonMetricsResponse(
                    calls = pythonMetrics.calls,
                    errors = pythonMetrics.errors,
                    timeouts = pythonMetrics.timeouts
                )
            )

            call.respond(response)
        }
    }
}