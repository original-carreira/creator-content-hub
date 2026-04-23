package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobState

interface JobRepository {

    fun create(jobId: String)

    fun markDone(
        jobId: String,
        transcription: String,
        summary: String,
        summaryCompletedAt: Long
    )

    fun markFailed(
        jobId: String,
        errorType: ErrorType,
        errorMessage: String
    )

    fun findById(jobId: String): JobState?

    fun exists(jobId: String): Boolean

    fun cleanup()
}