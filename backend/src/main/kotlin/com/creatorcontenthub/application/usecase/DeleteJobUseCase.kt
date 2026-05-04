package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.JobRepository

class DeleteJobUseCase(
    private val repository: JobRepository
) {
    fun execute(jobId: String) {
        repository.delete(jobId)
    }
}