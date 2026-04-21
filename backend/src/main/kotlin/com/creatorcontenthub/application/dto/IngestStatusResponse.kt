package com.creatorcontenthub.application.dto

import com.creatorcontenthub.domain.model.JobStatus

data class IngestStatusResponse(
    val jobId: String,
    val status: JobStatus
)
