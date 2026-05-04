package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.JobRepository

class DeleteJobsUseCase(
    private val repository: JobRepository
) {
    fun execute(jobIds: List<String>) {
        jobIds.forEach { repository.delete(it) }
    }
}