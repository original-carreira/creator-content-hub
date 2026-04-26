package com.creatorcontenthub.application.port

import com.creatorcontenthub.application.query.JobListItemView
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus

interface JobQueryRepository {

    fun findAll(
        status: JobStatus?,
        from: Long?,
        to: Long?,
        sortDirection: String,
        limit: Int,
        offset: Int
    ): List<JobListItemView>

    fun count(
        status: JobStatus?,
        from: Long?,
        to: Long?
    ): Long
}