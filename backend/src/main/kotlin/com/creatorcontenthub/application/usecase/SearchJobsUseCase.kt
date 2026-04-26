package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.JobSearchRepository
import kotlinx.serialization.Serializable

class SearchJobsUseCase(
    private val repository: JobSearchRepository
) {

    fun execute(query: String, limit: Int?, offset: Int?): SearchResult {

        val safeLimit = (limit ?: 20).coerceIn(1, 100)
        val safeOffset = (offset ?: 0).coerceAtLeast(0)

        val results = repository.search(query, safeLimit, safeOffset)
        val total = repository.count(query)

        return SearchResult(results, total)
    }
}

@Serializable
data class SearchResult(
    val items: List<com.creatorcontenthub.application.port.JobSearchResult>,
    val total: Long
)