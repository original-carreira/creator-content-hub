package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.SearchDebugInfo
import com.creatorcontenthub.application.port.JobSearchRepository
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.random.Random

class SearchJobsUseCase(
    private val repository: JobSearchRepository,
    private val searchMetrics: com.creatorcontenthub.infrastructure.metrics.SearchMetrics,
    private val analyticsEnabled: Boolean =
        System.getenv("SEARCH_ANALYTICS_ENABLED")?.toBoolean() ?: true
) {

    private val logger = LoggerFactory.getLogger(SearchJobsUseCase::class.java)

    private fun sanitizeQuery(input: String): String {
        return input
            .take(100) // limite tamanho
            .replace(Regex("[\\n\\r\\t]"), " ") // remove quebra de linha
    }

    private fun applyHighlight(snippet: String, query: String): String {
        if (query.isBlank()) return snippet

        val terms = query
            .lowercase()
            .split(" ")
            .filter { it.length >= 2 }

        var result = snippet

        for (term in terms) {
            val regex = Regex("(?i)(${Regex.escape(term)})")
            result = regex.replace(result) {
                "<span class=\"highlight\">${it.value}</span>"
            }
        }

        return result
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

        val result = run {

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

            val highlightedResults = results.map {
                it.copy(
                    snippet = applyHighlight(it.snippet, normalizedQuery)
                )
            }

            val mappedResults = if (debug) {
                highlightedResults.map {
                    it.copy(
                        debug = SearchDebugInfo(
                            rank = it.rank,
                            recencyScore = it.recencyScore ?: 0.0,
                            finalScore = it.finalScore ?: 0.0
                        )
                    )
                }
            } else {
                highlightedResults
            }

            SearchResult(mappedResults, total)
        }

        val durationMs = System.currentTimeMillis() - startTime

        val safeQuery = sanitizeQuery(query)

        // --- SEARCH ANALYTICS METRICS (LOW CARDINALITY) ---

        val hasResults = result.items.isNotEmpty()
        val hasStatusFilter = status != null

        searchMetrics.incrementSearchQuery(hasResults, hasStatusFilter)
        searchMetrics.recordResultsCount(result.items.size)
        searchMetrics.recordDuration(durationMs)

        if (!hasResults) {
            searchMetrics.incrementEmptyQuery(hasStatusFilter)

            logger.info(
                "event=search_zero_results q={} requestId={}",
                safeQuery,
                "N/A"
            )
        }

        // --- SEARCH ANALYTICS LOG (NOVO) ---

        if (analyticsEnabled && shouldSample()) {
            logger.info(
                "event=search_analytics q={} result_count={} has_results={} duration_ms={}",
                safeQuery,
                result.items.size,
                hasResults,
                durationMs
            )
        }

        // --- LOGS OPERACIONAIS (SEMPRE ATIVOS, MAS SANITIZADOS) ---

        logger.info(
            "event=search_executed q={} results={} has_results={} duration_ms={}",
            safeQuery,
            result.items.size,
            hasResults,
            durationMs
        )

        if (hasResults) {
            val top = result.items.first()
            logger.info(
                "event=search_top_result job_id={} score={} rank={} recency={} q={}",
                top.jobId,
                top.finalScore ?: 0.0,
                top.rank,
                top.recencyScore ?: 0.0,
                safeQuery
            )
        }

        if (debug && hasResults) {

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