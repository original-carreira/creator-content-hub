package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResumeJobResponse(
    val jobId: String,
    val status: String,
    val stage: String,
    val resumed: Boolean,
    val message: String
)
