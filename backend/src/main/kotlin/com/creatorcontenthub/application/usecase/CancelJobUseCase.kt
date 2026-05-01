package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStatus

class CancelJobUseCase(
    private val repository: JobRepository
) {
    fun execute(jobId: String) {

        val job = repository.findById(jobId)
            ?: throw IllegalArgumentException("Job not found")

        if (job.status == JobStatus.DONE || job.status == JobStatus.FAILED) {
            throw IllegalStateException("Job already finished")
        }

        repository.updateStatus(jobId, JobStatus.CANCELED)
    }
}