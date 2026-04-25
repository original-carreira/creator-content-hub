package com.creatorcontenthub.infrastructure.metrics

import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*

object JvmMetricsConfig {

    fun register() {

        val registry = PrometheusRegistry.registry

        // JVM
        ClassLoaderMetrics().bindTo(registry)
        JvmMemoryMetrics().bindTo(registry)
        JvmGcMetrics().bindTo(registry)
        JvmThreadMetrics().bindTo(registry)

        // Sistema
        ProcessorMetrics().bindTo(registry)
        UptimeMetrics().bindTo(registry)
    }
}