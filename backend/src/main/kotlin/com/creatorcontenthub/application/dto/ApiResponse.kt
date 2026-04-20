package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>( // 🔥 AGORA GENÉRICO
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
)