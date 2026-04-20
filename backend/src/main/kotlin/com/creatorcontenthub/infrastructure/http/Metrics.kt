package com.creatorcontenthub.infrastructure.http

import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val StartTimeKey = AttributeKey<Long>("StartTime")

// 🔹 MÉTRICAS GLOBAIS
private val totalRequests = AtomicLong(0)
private val routeCounters = ConcurrentHashMap<String, AtomicLong>()

// 🔹 NOVO: MÉTRICAS DO PYTHON (centralizadas)
val pythonCalls = AtomicLong(0)
val pythonErrors = AtomicLong(0)
val pythonTimeouts = AtomicLong(0)

fun Application.configureMetrics() {

    intercept(ApplicationCallPipeline.Monitoring) {

        val startTime = System.currentTimeMillis()
        call.attributes.put(StartTimeKey, startTime)

        totalRequests.incrementAndGet()

        // 🔹 NORMALIZA PATH (evita problemas futuros)
        val path = call.request.path().substringBefore("?")

        routeCounters
            .computeIfAbsent(path) { AtomicLong(0) }
            .incrementAndGet()
    }

    intercept(ApplicationCallPipeline.Fallback) {

        val startTime = call.attributes.getOrNull(StartTimeKey)
            ?: return@intercept

        val duration = System.currentTimeMillis() - startTime

        call.attributes.put(DurationKey, duration)
    }
}

// 🔹 EXPOSIÇÃO
fun getTotalRequests(): Long = totalRequests.get()

fun getRouteMetrics(): Map<String, Long> =
    routeCounters.mapValues { it.value.get() }

// 🔹 NOVO: EXPOSIÇÃO PYTHON
fun getPythonMetrics(): PythonMetrics =
    PythonMetrics(
        calls = pythonCalls.get(),
        errors = pythonErrors.get(),
        timeouts = pythonTimeouts.get()
    )