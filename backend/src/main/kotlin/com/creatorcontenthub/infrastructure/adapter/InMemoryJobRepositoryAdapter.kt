package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobState
import java.util.concurrent.ConcurrentHashMap

class InMemoryJobRepository : JobRepository {

    private val store = ConcurrentHashMap<String, JobState>()

    override fun create(jobId: String, job: JobState) {
        store[jobId] = job
    }

    override fun update(jobId: String, job: JobState) {
        store[jobId] = job
    }

    override fun findById(jobId: String): JobState? {
        return store[jobId]
    }
}