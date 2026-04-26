package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: ApiError? = null
)