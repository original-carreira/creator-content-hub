package com.creatorcontenthub.application.port

import com.creatorcontenthub.application.dto.SearchDebugInfo
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class JobSearchResult(
    val jobId: String,
    val status: String,
    val createdAt: String,
    val snippet: String,
    val rank: Double,
    val recencyScore: Double? = null,
    val finalScore: Double? = null,
    val debug: SearchDebugInfo? = null
)

interface JobSearchRepository {

    fun search(
        query: String,
        rawQuery: String,
        status: String?,
        from: Long?,
        to: Long?,
        limit: Int,
        offset: Int
    ): List<JobSearchResult>

    fun count(
        query: String,
        rawQuery: String,
        status: String?,
        from: Long?,
        to: Long?
    ): Long
}
