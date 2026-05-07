package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.domain.model.JobState

interface PipelineStep {

    suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState
}