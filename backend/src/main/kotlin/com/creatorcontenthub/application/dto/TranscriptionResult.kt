package com.creatorcontenthub.application.dto

data class TranscriptionResult(
    val text: String,
    val language: String? = null,
    val durationMs: Long
)
