package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.JobState

interface JobRepository {

    fun create(jobId: String, job: JobState)

    fun update(jobId: String, job: JobState)

    fun findById(jobId: String): JobState?
}