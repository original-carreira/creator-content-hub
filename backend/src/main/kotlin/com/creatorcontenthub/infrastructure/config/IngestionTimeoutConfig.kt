package com.creatorcontenthub.infrastructure.config


data class IngestionTimeoutConfig(
    val downloadTimeoutMs: Long = 10 * 60 * 1000,       // 10 min
    val transcriptionTimeoutMs: Long = 15 * 60 * 1000,  // preparado para próxima fase
    val summarizationTimeoutMs: Long = 2 * 60 * 1000    // preparado
)
