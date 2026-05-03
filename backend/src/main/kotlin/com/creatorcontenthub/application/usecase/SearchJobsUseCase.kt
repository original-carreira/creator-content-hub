package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.SearchDebugInfo
import com.creatorcontenthub.application.port.JobSearchRepository
import io.micrometer.core.instrument.DistributionSummary
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import kotlin.random.Random

class SearchJobsUseCase(
    private val repository: JobSearchRepository,
    private val meterRegistry: MeterRegistry,
    private val analyticsEnabled: Boolean =
        System.getenv("SEARCH_ANALYTICS_ENABLED")?.toBoolean() ?: true
) {

    private val logger = LoggerFactory.getLogger(SearchJobsUseCase::class.java)

    private val timer: Timer =
        Timer.builder("search_query_duration")
            .description("Search query execution time")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(meterRegistry)

    private fun sanitizeQuery(input: String): String {
        return input
            .take(100) // limite tamanho
            .replace(Regex("[\\n\\r\\t]"), " ") // remove quebra de linha
    }

    private fun toTsQuery(q: String): String {
        return q.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" & ") { "$it:*" }
    }

    private fun shouldSample(rate: Double = 0.1): Boolean {
        return Random.nextDouble() < rate
    }

    fun execute(
        query: String,
        status: String?,
        from: Long?,
        to: Long?,
        limit: Int?,
        offset: Int?,
        debug: Boolean
    ): SearchResult {

        val startTime = System.currentTimeMillis()

        val result = timer.recordCallable {

            val normalizedQuery = query
                .trim()
                .lowercase()
                .replace(Regex("\\s+"), " ")

            if (normalizedQuery.isBlank()) {
                throw IllegalArgumentException("Query cannot be empty")
            }

            val tsQuery = toTsQuery(normalizedQuery)
            val safeLimit = (limit ?: 20).coerceIn(1, 100)
            val safeOffset = (offset ?: 0).coerceAtLeast(0)

            val results = repository.search(
                query = tsQuery,
                rawQuery = normalizedQuery, // NOVO PARAM
                status = status,
                from = from,
                to = to,
                limit = safeLimit,
                offset = safeOffset
            )

            val total = repository.count(
                query = tsQuery,
                rawQuery = normalizedQuery, // MESMA LÓGICA
                status = status,
                from = from,
                to = to
            )

            val mappedResults = if (debug) {
                results.map {
                    it.copy(
                        debug = SearchDebugInfo(
                            rank = it.rank,
                            recencyScore = it.recencyScore ?: 0.0,
                            finalScore = it.finalScore ?: it.rank
                        )
                    )
                }
            } else {
                results
            }

            SearchResult(mappedResults, total)
        }

        val durationMs = System.currentTimeMillis() - startTime

        val safeQuery = sanitizeQuery(query)

        // --- SEARCH ANALYTICS METRICS (LOW CARDINALITY) ---

        val hasResults = result.items.isNotEmpty().toString()
        val statusFilter = if (status != null) "present" else "absent"

        Counter.builder("search_query_total")
            .tag("has_results", hasResults)
            .tag("status_filter", statusFilter)
            .register(meterRegistry)
            .increment()

        if (result.items.isEmpty()) {

            logger.info(
                "event=search_zero_results q={} requestId={}",
                safeQuery,
                "N/A"
            )

            Counter.builder("search_query_empty_total")
                .tag("status_filter", statusFilter)
                .register(meterRegistry)
                .increment()
        }

        // --- RESULT DISTRIBUTION METRIC ---

        DistributionSummary.builder("search_results_count")
            .tag("status_filter", statusFilter)
            .register(meterRegistry)
            .record(result.items.size.toDouble())

        // --- SEARCH ANALYTICS LOG (NOVO) ---

        if (analyticsEnabled && shouldSample()) {
            logger.info(
                "event=search_analytics q={} result_count={} has_results={} duration_ms={}",
                safeQuery,
                result.items.size,
                result.items.isNotEmpty(),
                durationMs
            )
        }

        // --- LOGS OPERACIONAIS (SEMPRE ATIVOS, MAS SANITIZADOS) ---

        logger.info(
            "event=search_executed q={} results={} has_results={} duration_ms={}",
            safeQuery,
            result.items.size,
            result.items.isNotEmpty(),
            durationMs
        )

        if (result.items.isNotEmpty()) {
            val top = result.items.first()
            logger.info(
                "event=search_top_result job_id={} score={}",
                top.jobId,
                top.finalScore ?: 0.0
            )
        }

        if (debug && result.items.isNotEmpty()) {

            val topScore = result.items.first().finalScore ?: 0.0
            val avgScore = result.items.map { it.finalScore ?: 0.0 }.average()

            logger.info(
                "event=search_debug top_score={} avg_score={}",
                topScore,
                avgScore
            )
        }

        return result
    }
}

@Serializable
data class SearchResult(
    val items: List<com.creatorcontenthub.application.port.JobSearchResult>,
    val total: Long
)