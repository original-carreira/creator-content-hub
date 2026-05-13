package com.creatorcontenthub.domain.model

data class JobQueueItem(
    val id: Long? = null,
    val jobId: String,
    val status: QueueStatus,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val attempts: Int = 0,
    val errorMessage: String? = null,
    val claimedBy: String? = null,
    val lastHeartbeatAt: Long? = null
)
