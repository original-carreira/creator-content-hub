package com.creatorcontenthub.domain.model

data class JobState(
    val status: JobStatus,
    val createdAt: Long,
    val startedAt: Long,
    val videoId: String? = null,
    val finishedAt: Long? = null,

    val errorType: ErrorType? = null,
    val errorMessage: String? = null,
    val transcription: String? = null,
    val transcriptionCompletedAt: Long? = null,
    val summary: String? = null,
    val summaryCompletedAt: Long? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,

    val transcriptionPath: String? = null,
    val summaryPath: String? = null,
    val audioPath: String? = null
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

        if (status.isFinal()) {
            require(finishedAt != null) {
                "Final states must contain finishedAt"
            }
        }

        if (!status.isFinal()) {
            require(finishedAt == null) {
                "Non-final states cannot contain finishedAt"
            }
        }

        // DONE precisa de transcription
        if (status == JobStatus.DONE) {
            require(!transcription.isNullOrBlank()) {
                "DONE state must contain transcription"
            }

            require(!summary.isNullOrBlank()) {
                "DONE state must contain summary"
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

        // CANCELED não pode ter dados derivados
        if (status == JobStatus.CANCELED) {
            require(transcription == null) {
                "CANCELED state cannot contain transcription"
            }

            require(summary == null) {
                "CANCELED state cannot contain summary"
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

        // summary timing
        if (summary != null) {
            val completedAt = summaryCompletedAt

            require(completedAt != null) {
                "summaryCompletedAt must be present when summary exists"
            }

            require(completedAt >= startedAt) {
                "summaryCompletedAt must be >= startedAt"
            }

            if (finishedAt != null) {
                require(completedAt <= finishedAt) {
                    "summaryCompletedAt must be <= finishedAt"
                }
            }
        }

        if (summary == null) {
            require(summaryCompletedAt == null) {
                "summaryCompletedAt cannot exist without summary"
            }
        }

        if (status == JobStatus.DONE) {
            require(!transcriptionPath.isNullOrBlank()) {
                "DONE state must contain transcriptionPath"
            }

            require(!summaryPath.isNullOrBlank()) {
                "DONE state must contain summaryPath"
            }
        }
    }

    companion object {

        fun started(now: Long, videoId: String?): JobState {
            return JobState(
                status = JobStatus.PROCESSING,
                createdAt = now,
                startedAt = now,
                videoId = videoId
            )
        }
    }

    fun markDone(
        transcription: String,
        summary: String,
        finishedAt: Long
    ): JobState {
        return copy(
            status = JobStatus.DONE,
            transcription = transcription,
            transcriptionCompletedAt = finishedAt,
            summary = summary,
            summaryCompletedAt = finishedAt,
            finishedAt = finishedAt,
            errorType = null,
            errorMessage = null
        )
    }

    fun markFailed(
        errorType: ErrorType,
        errorMessage: String,
        finishedAt: Long
    ): JobState {
        return copy(
            status = JobStatus.FAILED,
            errorType = errorType,
            errorMessage = errorMessage,
            finishedAt = finishedAt,
            transcription = null,
            transcriptionCompletedAt = null,
            summary = null,
            summaryCompletedAt = null
        )
    }

    fun markCanceled(finishedAt: Long): JobState {
        return copy(
            status = JobStatus.CANCELED,
            finishedAt = finishedAt,
            errorType = null,
            errorMessage = "Canceled by user",
            transcription = null,
            transcriptionCompletedAt = null,
            summary = null,
            summaryCompletedAt = null
        )
    }

    private fun JobStatus.isFinal(): Boolean {
        return this == JobStatus.DONE ||
                this == JobStatus.FAILED ||
                this == JobStatus.CANCELED
    }
}