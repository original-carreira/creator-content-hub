package com.creatorcontenthub.application.dto

data class IngestStatusResponse(
    val jobId: String,
    val status: String,
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val transcription: String?,
    val errorType: String?,
    val errorMessage: String?
)