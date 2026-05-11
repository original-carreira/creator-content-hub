package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.JobQueueItem

interface JobQueueRepository {

    fun enqueue(item: JobQueueItem)

    fun findNextPending(): JobQueueItem?

    fun markProcessing(queueId: Long, startedAt: Long)

    fun markCompleted(queueId: Long, completedAt: Long)

    fun markFailed(
        queueId: Long,
        completedAt: Long,
        errorMessage: String?
    )

}