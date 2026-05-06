package com.creatorcontenthub.application.dto

import com.creatorcontenthub.domain.model.JobState
import kotlinx.serialization.Serializable

@Serializable
data class JobListItemResponse(
    val jobId: String,
    val status: String,
    val createdAt: Long,
    val finishedAt: Long?,
    val hasTranscription: Boolean,
    val hasSummary: Boolean,

    val title: String? = null,
    val thumbnailUrl: String? = null,
    val snippet: String? = null
) {
    companion object {
        fun from(jobId: String, job: JobState): JobListItemResponse {
            return JobListItemResponse(
                jobId = jobId,
                status = job.status.name,
                createdAt = job.createdAt,
                finishedAt = job.finishedAt,
                hasTranscription = !job.transcription.isNullOrBlank(),
                hasSummary = !job.summary.isNullOrBlank(),

                title = job.title,
                thumbnailUrl = job.thumbnailUrl,
                snippet = job.summary?.take(120)
            )
        }
    }
}