package com.creatorcontenthub.application.service

import com.creatorcontenthub.domain.model.ExistingJobDecision
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus

class ExistingJobResolver {

    fun resolve(job: JobState): ExistingJobDecision {

        return when {

            job.status == JobStatus.DONE &&
                    job.stage == JobStage.COMPLETED -> {
                ExistingJobDecision.REUSE_COMPLETED
            }

            job.status == JobStatus.PROCESSING -> {
                ExistingJobDecision.RETURN_PROCESSING
            }

            job.status == JobStatus.FAILED ||
                    job.status == JobStatus.CANCELED -> {
                ExistingJobDecision.ALLOW_RESUME
            }

            else -> {
                ExistingJobDecision.CREATE_NEW
            }
        }
    }
}