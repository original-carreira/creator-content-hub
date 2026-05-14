package com.creatorcontenthub.domain.model

enum class RetryDecision {
    RETRYABLE,
    TERMINAL,
    DLQ
}