package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.JobQueueItem

interface JobQueueRepository {

    fun enqueue(item: JobQueueItem)

    fun claimNextPending(
        workerId: String,
        startedAt: Long
    ): JobQueueItem?

    fun markCompleted(queueId: Long, completedAt: Long)

    fun markFailed(
        queueId: Long,
        completedAt: Long,
        errorMessage: String?
    )

}