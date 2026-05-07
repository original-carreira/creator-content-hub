package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState

interface PipelineStep {

    val supportedStage: JobStage

    suspend fun execute(
        jobId: String,
        job: JobState
    ): JobState
}