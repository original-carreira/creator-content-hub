package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.SearchDebugInfo
import com.creatorcontenthub.application.port.JobSearchRepository
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer

class SearchJobsUseCase(
    private val repository: JobSearchRepository,
    private val meterRegistry: MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(SearchJobsUseCase::class.java)

    // 🔹 Metrics
    private val searchCounter: Counter =
        Counter.builder("search_queries_total")
            .description("Total number of search queries")
            .register(meterRegistry)

    private val emptyCounter: Counter =
        Counter.builder("search_queries_empty_total")
            .description("Search queries with no results")
            .register(meterRegistry)

    private val timer: Timer =
        Timer.builder("search_query_duration")
            .description("Search query execution time")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(meterRegistry)

    fun execute(
        query: String,
        status: String?,
        from: Long?,
        to: Long?,
        limit: Int?,
        offset: Int?,
        debug: Boolean
    ): SearchResult {

        val result = timer.recordCallable {

            val safeLimit = (limit ?: 20).coerceIn(1, 100)
            val safeOffset = (offset ?: 0).coerceAtLeast(0)

            val results = repository.search(
                query = query,
                status = status,
                from = from,
                to = to,
                limit = safeLimit,
                offset = safeOffset
            )

            val total = repository.count(
                query = query,
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

        // 🔹 Metrics counters
        searchCounter.increment()

        if (result.items.isEmpty()) {
            emptyCounter.increment()
        }

        // 🔹 Structured log (principal)
        logger.info(
            "event=search_executed q={} status={} from={} to={} results={}",
            query,
            status,
            from,
            to,
            result.items.size
        )

        // 🔹 Top result log
        if (result.items.isNotEmpty()) {
            val top = result.items.first()
            logger.info(
                "event=search_top_result job_id={} score={}",
                top.jobId,
                top.finalScore ?: 0.0
            )
        }

        // 🔹 Debug log (somente quando solicitado)
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