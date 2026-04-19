package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val success: Boolean,
    val data: ProcessTextResponse? = null,
    val error: ApiError? = null
)