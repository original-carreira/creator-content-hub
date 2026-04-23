package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore

class InMemoryJobRepositoryAdapter(
    private val store: InMemoryJobStatusStore
) : JobRepository {

    override fun create(jobId: String) = store.create(jobId)

    override fun markDone(
        jobId: String,
        transcription: String,
        summary: String,
        summaryCompletedAt: Long
    ) = store.markDone(jobId, transcription, summary, summaryCompletedAt)

    override fun markFailed(
        jobId: String,
        errorType: ErrorType,
        errorMessage: String
    ) = store.markFailed(jobId, errorType, errorMessage)

    override fun findById(jobId: String) = store.get(jobId)

    override fun exists(jobId: String) = store.exists(jobId)

    override fun cleanup() = store.cleanup()
}