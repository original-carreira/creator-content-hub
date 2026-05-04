package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class JobResponse(
    val status: String,
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val transcription: String?,
    val summary: String?,
    val title: String? = null
)
