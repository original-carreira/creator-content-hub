package com.creatorcontenthub.domain.model

enum class ExistingJobDecision {
    REUSE_COMPLETED,
    RETURN_PROCESSING,
    ALLOW_RESUME,
    CREATE_NEW
}