package com.creatorcontenthub.infrastructure.runtime

@kotlinx.serialization.Serializable
data class RuntimeEvent(
    val jobId: String,
    val event: String,
    val status: String? = null,
    val stage: String? = null,
    val message: String? = null,
    val progress: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
