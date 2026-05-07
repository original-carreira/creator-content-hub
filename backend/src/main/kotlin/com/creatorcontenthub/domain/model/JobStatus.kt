package com.creatorcontenthub.domain.model

enum class JobStatus {
    PROCESSING,
    DONE,
    FAILED,
    CANCELED;

    fun isFinal(): Boolean {
        return this == DONE ||
                this == FAILED ||
                this == CANCELED
    }
}