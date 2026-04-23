package com.creatorcontenthub.domain.model

data class JobState(
    val status: JobStatus,
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,

    val errorType: ErrorType? = null,
    val errorMessage: String? = null,
    val transcription: String? = null,
    val transcriptionCompletedAt: Long? = null,
    val summary: String? = null,
    val summaryCompletedAt: Long? = null
) {

    init {

        if (status == JobStatus.FAILED) {
            require(errorType != null) {
                "FAILED state must contain errorType"
            }
        }

        if (status == JobStatus.DONE) {
            require(errorType == null) {
                "DONE state cannot contain errorType"
            }
        }

        require(startedAt >= createdAt) {
            "startedAt must be >= createdAt"
        }

        if (finishedAt != null) {
            require(finishedAt >= startedAt) {
                "finishedAt must be >= startedAt"
            }
        }

        if (status == JobStatus.DONE || status == JobStatus.FAILED) {
            require(finishedAt != null) {
                "Final states must contain finishedAt"
            }
        }

        if (status != JobStatus.DONE && status != JobStatus.FAILED) {
            require(finishedAt == null) {
                "Non-final states cannot contain finishedAt"
            }
        }

        // DONE precisa de transcription (mantido)
        if (status == JobStatus.DONE) {
            require(!transcription.isNullOrBlank()) {
                "DONE state must contain transcription"
            }
        }

        // FAILED não pode ter dados derivados
        if (status == JobStatus.FAILED) {
            require(transcription == null) {
                "FAILED state cannot contain transcription"
            }

            require(summary == null) {
                "FAILED state cannot contain summary"
            }
        }

        // transcription timing
        if (transcription != null) {
            val completedAt = transcriptionCompletedAt

            require(completedAt != null) {
                "transcriptionCompletedAt must be present when transcription exists"
            }

            require(completedAt >= startedAt) {
                "transcriptionCompletedAt must be >= startedAt"
            }
        }

        if (transcription == null) {
            require(transcriptionCompletedAt == null) {
                "transcriptionCompletedAt cannot exist without transcription"
            }
        }

        // summary timing (mantido)
        if (summary != null) {
            val completedAt = summaryCompletedAt

            require(completedAt != null) {
                "summaryCompletedAt must be present when summary exists"
            }

            require(completedAt >= startedAt) {
                "summaryCompletedAt must be >= startedAt"
            }
        }

        if (summary == null) {
            require(summaryCompletedAt == null) {
                "summaryCompletedAt cannot exist without summary"
            }
        }
    }
}