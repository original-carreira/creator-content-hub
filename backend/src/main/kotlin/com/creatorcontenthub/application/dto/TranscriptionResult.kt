package com.creatorcontenthub.application.dto

import com.creatorcontenthub.domain.model.Transcript

data class TranscriptionResult(
    val text: String,
    val transcript: Transcript? = null,
    val language: String? = null,
    val durationMs: Long
)
