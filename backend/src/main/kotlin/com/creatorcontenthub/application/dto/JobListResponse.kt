package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class JobListResponse(
    val items: List<JobListItemResponse>,
    val total: Long,
    val limit: Int,
    val offset: Int
)
