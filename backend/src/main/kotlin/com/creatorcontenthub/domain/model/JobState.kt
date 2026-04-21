package com.creatorcontenthub.domain.model

data class JobState(
    val status: JobStatus,
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,

    val errorType: ErrorType? = null,
    val errorMessage: String? = null
)

