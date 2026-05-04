package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus

interface JobRepository {

    fun create(jobId: String, job: JobState)

    fun update(jobId: String, job: JobState)

    fun delete(jobId: String)

    fun findById(jobId: String): JobState?

    fun updateStatus(jobId: String, status: JobStatus)

    fun isCanceled(jobId: String): Boolean

    fun markCanceledIfNotFinal(jobId: String): Boolean

    fun findByVideoId(videoId: String): JobState?

    fun findWithIdByVideoId(videoId: String): Pair<String, JobState>?
}