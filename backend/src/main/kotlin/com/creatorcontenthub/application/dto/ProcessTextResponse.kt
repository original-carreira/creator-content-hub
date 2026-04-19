package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProcessTextResponse(
    val result: String
)