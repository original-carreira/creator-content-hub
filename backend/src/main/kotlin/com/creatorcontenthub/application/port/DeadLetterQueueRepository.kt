package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.DeadLetterQueueItem

interface DeadLetterQueueRepository {

    fun insert(
        item: DeadLetterQueueItem
    ): Long

    fun findById(
        id: Long
    ): DeadLetterQueueItem?

    fun list(
        limit: Int,
        offset: Int
    ): List<DeadLetterQueueItem>

    fun delete(
        id: Long
    ): Boolean
}