package com.creatorcontenthub.infrastructure.metrics

import io.micrometer.core.instrument.*
import java.util.concurrent.TimeUnit

class SearchMetrics(
    private val meterRegistry: MeterRegistry
) {

    // --- COUNTERS CACHEADOS ---

    private val searchQueryCounters = mutableMapOf<Pair<Boolean, Boolean>, Counter>()

    private val emptyQueryCounters = mutableMapOf<Boolean, Counter>()

    // --- SUMMARY CACHEADO ---

    private val resultsSummary: DistributionSummary =
        DistributionSummary.builder("search_results_count")
            .description("Number of results returned per search query")
            .publishPercentileHistogram() // 👈 CRÍTICO
            .serviceLevelObjectives(
                1.0, 3.0, 5.0, 10.0, 20.0
            ) // buckets uteis
            .register(meterRegistry)

    // --- TIMER CACHEADO ---

    private val timer: Timer =
        Timer.builder("search_query_duration")
            .description("Search query execution time")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(meterRegistry)

    // =============================
    // PUBLIC API
    // =============================

    fun incrementSearchQuery(hasResults: Boolean, hasStatusFilter: Boolean) {
        val key = hasResults to hasStatusFilter

        val counter = searchQueryCounters.getOrPut(key) {
            Counter.builder("search_query_total")
                .tag("has_results", hasResults.toString())
                .tag("status_filter", if (hasStatusFilter) "present" else "absent")
                .register(meterRegistry)
        }

        counter.increment()
    }

    fun incrementEmptyQuery(hasStatusFilter: Boolean) {
        val counter = emptyQueryCounters.getOrPut(hasStatusFilter) {
            Counter.builder("search_query_empty_total")
                .tag("status_filter", if (hasStatusFilter) "present" else "absent")
                .register(meterRegistry)
        }

        counter.increment()
    }

    fun recordResultsCount(count: Int) {
        resultsSummary.record(count.toDouble())
    }

    fun recordDuration(durationMs: Long) {
        timer.record(durationMs, TimeUnit.MILLISECONDS)
    }
}