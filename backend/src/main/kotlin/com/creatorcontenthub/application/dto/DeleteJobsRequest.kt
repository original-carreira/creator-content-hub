package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeleteJobsRequest(
    val jobIds: List<String>
)
