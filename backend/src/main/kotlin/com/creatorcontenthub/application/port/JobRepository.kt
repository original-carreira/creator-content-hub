package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus

interface JobRepository {

    fun create(jobId: String, job: JobState)

    fun update(jobId: String, job: JobState)

    fun findById(jobId: String): JobState?

    fun updateStatus(jobId: String, status: JobStatus)

    fun isCanceled(jobId: String): Boolean

    fun markCanceledIfNotFinal(jobId: String): Boolean
}