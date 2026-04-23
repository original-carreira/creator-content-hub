package com.creatorcontenthub.application.dto

import java.time.Instant

data class SummarizationResult(
    val summary: String,
    val generatedAt: Instant,
    val inputTruncated: Boolean
)