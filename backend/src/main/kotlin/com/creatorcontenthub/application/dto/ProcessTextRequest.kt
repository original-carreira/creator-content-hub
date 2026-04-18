package com.creatorcontenthub.application.dto
import kotlinx.serialization.Serializable

@Serializable
data class ProcessTextRequest(
    val text: String
)