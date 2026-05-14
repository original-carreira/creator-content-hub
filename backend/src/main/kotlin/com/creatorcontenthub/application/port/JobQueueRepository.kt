package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.JobQueueItem

interface JobQueueRepository {

    fun enqueue(item: JobQueueItem)

    fun claimNextPending(
        workerId: String,
        startedAt: Long
    ): JobQueueItem?

    fun updateHeartbeat(
        queueId: Long,
        workerId: String,
        heartbeatAt: Long
    ): Boolean

    fun findExpiredProcessingJobs(
        heartbeatTimeoutBefore: Long
    ): List<JobQueueItem>

    fun requeueOrphanedJob(
        queueId: Long
    ): Boolean

    fun markFailedMaxAttempts(
        queueId: Long,
        completedAt: Long,
        errorMessage: String?
    ): Boolean

    fun scheduleRetry(
        queueId: Long,
        retryAt: Long,
        errorMessage: String?
    )

    fun markAsDeadLetter(
        queueId: Long,
        completedAt: Long,
        errorMessage: String?
    )

    fun markCompleted(queueId: Long, completedAt: Long)

    fun markFailed(
        queueId: Long,
        completedAt: Long,
        errorMessage: String?
    )

}