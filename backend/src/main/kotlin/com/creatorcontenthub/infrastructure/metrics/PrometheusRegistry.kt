package com.creatorcontenthub.infrastructure.metrics

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

object PrometheusRegistry {

    val registry: PrometheusMeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}