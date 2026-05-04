package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import java.util.concurrent.ConcurrentHashMap

class InMemoryJobRepository : JobRepository {

    private val store = ConcurrentHashMap<String, JobState>()

    override fun create(jobId: String, job: JobState) {
        store[jobId] = job
    }

    override fun update(jobId: String, job: JobState) {
        store[jobId] = job
    }

    override fun delete(jobId: String) {
        store.remove(jobId)
    }

    override fun findById(jobId: String): JobState? {
        return store[jobId]
    }

    override fun updateStatus(jobId: String, status: JobStatus) {
        val current = store[jobId] ?: return

        store[jobId] = current.copy(
            status = status,
            finishedAt = System.currentTimeMillis()
        )
    }

    override fun isCanceled(jobId: String): Boolean {
        return store[jobId]?.status == JobStatus.CANCELED
    }

    override fun markCanceledIfNotFinal(jobId: String): Boolean {

        val job = store[jobId] ?: return false

        if (job.status.isFinal()) {
            return false
        }

        val updated = job.copy(
            status = JobStatus.CANCELED,
            finishedAt = System.currentTimeMillis()
        )

        store[jobId] = updated

        return true
    }
}