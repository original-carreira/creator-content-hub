package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.dto.JobListItemResponse
import com.creatorcontenthub.application.dto.JobListResponse
import com.creatorcontenthub.application.port.JobQueryRepository
import com.creatorcontenthub.domain.model.JobStatus

class ListJobsUseCase(
    private val repository: JobQueryRepository
) {

    fun execute(
        status: String?,
        from: Long?,
        to: Long?,
        sort: String?,
        order: String?,
        limit: Int?,
        offset: Int?
    ): JobListResponse {

        val parsedStatus = status?.let {
            runCatching { JobStatus.valueOf(it) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $it") }
        }

        if (from != null && from < 0) {
            throw IllegalArgumentException("Invalid 'from' timestamp")
        }

        if (to != null && to < 0) {
            throw IllegalArgumentException("Invalid 'to' timestamp")
        }

        if (from != null && to != null && from > to) {
            throw IllegalArgumentException("Invalid date range: from > to")
        }

        val safeLimit = (limit ?: 20).coerceIn(1, 100)
        val safeOffset = (offset ?: 0).coerceAtLeast(0)

        val jobs = repository.findAll(
            parsedStatus,
            from,
            to,
            sort,
            order,
            safeLimit,
            safeOffset
        )
        val total = repository.count(parsedStatus, from, to)

        val items = jobs.map {
            JobListItemResponse(
                jobId = it.jobId,
                status = it.status.name,
                createdAt = it.createdAt,
                finishedAt = it.finishedAt,
                hasTranscription = it.hasTranscription,
                hasSummary = it.hasSummary
            )
        }

        return JobListResponse(
            items = items,
            total = total,
            limit = safeLimit,
            offset = safeOffset
        )
    }
}