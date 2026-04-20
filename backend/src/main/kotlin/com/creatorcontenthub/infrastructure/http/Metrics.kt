package com.creatorcontenthub.infrastructure.http

import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// 🔥 IMPORTANTE: NÃO recriar DurationKey aqui
// ele já vem do arquivo de extensions
// import com.creatorcontenthub.infrastructure.http.DurationKey

private val StartTimeKey = AttributeKey<Long>("StartTime")

// contador global
private val totalRequests = AtomicLong(0)

// contador por rota
private val routeCounters = ConcurrentHashMap<String, AtomicLong>()

fun Application.configureMetrics() {

    // 🔹 INÍCIO da requisição
    intercept(ApplicationCallPipeline.Monitoring) {

        val startTime = System.currentTimeMillis()
        call.attributes.put(StartTimeKey, startTime)

        // contador global
        totalRequests.incrementAndGet()

        // contador por rota
        val path = call.request.path()
        routeCounters
            .computeIfAbsent(path) { AtomicLong(0) }
            .incrementAndGet()
    }

    // 🔹 FINAL da requisição
    intercept(ApplicationCallPipeline.Fallback) {

        val startTime = call.attributes.getOrNull(StartTimeKey)
            ?: return@intercept

        val duration = System.currentTimeMillis() - startTime

        // 🔥 usa a MESMA key do helper
        call.attributes.put(DurationKey, duration)
    }
}

// 🔹 EXPOSIÇÃO DE MÉTRICAS
fun getTotalRequests(): Long = totalRequests.get()

fun getRouteMetrics(): Map<String, Long> =
    routeCounters.mapValues { it.value.get() }