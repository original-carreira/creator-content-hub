package com.creatorcontenthub.application.port

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class JobSearchResult(
    val jobId: String,
    val status: String,
    val createdAt: String,
    val snippet: String,
    val rank: Double
)

interface JobSearchRepository {

    fun search(query: String, limit: Int, offset: Int): List<JobSearchResult>

    fun count(query: String): Long
}
