package com.creatorcontenthub.domain.model

data class DeadLetterQueueItem(
    val id: Long? = null,
    val jobId: String,
    val queueId: Long? = null,
    val stage: String? = null,
    val errorMessage: String? = null,
    val failedAt: Long,
    val attempts: Int,
    val workerId: String? = null,
    val payloadSnapshot: String? = null
)
