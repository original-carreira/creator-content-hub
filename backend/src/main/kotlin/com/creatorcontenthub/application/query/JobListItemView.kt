package com.creatorcontenthub.application.query

import com.creatorcontenthub.domain.model.JobStatus

data class JobListItemView(
    val jobId: String,
    val status: JobStatus,
    val createdAt: Long,
    val finishedAt: Long?,
    val hasTranscription: Boolean,
    val hasSummary: Boolean,

    val title: String?,
    val thumbnailUrl: String?,
    val summary: String?
)
