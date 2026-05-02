package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStatus

class CancelJobUseCase(
    private val repository: JobRepository
) {
    fun execute(jobId: String) {

        val updated = repository.markCanceledIfNotFinal(jobId)

        if (!updated) {
            throw IllegalStateException("Job already finished or not found")
        }
    }
}