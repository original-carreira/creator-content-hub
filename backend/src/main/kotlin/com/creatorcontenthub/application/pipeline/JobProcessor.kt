package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobState
import org.slf4j.LoggerFactory

class JobProcessor(
    private val jobRepository: JobRepository,
    private val steps: List<PipelineStep>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun process(
        jobId: String,
        initialState: JobState
    ): JobState {

        var current = initialState

        for (step in steps) {

            val stageBefore = current.stage
            val startedAt = System.currentTimeMillis()

            logger.info(
                "event=stage_started jobId={} stage={}",
                jobId,
                stageBefore
            )

            try {

                current = step.execute(jobId, current)

                val duration = System.currentTimeMillis() - startedAt

                logger.info(
                    "event=stage_completed jobId={} stage={} durationMs={}",
                    jobId,
                    current.stage,
                    duration
                )

            } catch (ex: Exception) {

                val duration = System.currentTimeMillis() - startedAt

                logger.error(
                    "event=stage_failed jobId={} stage={} durationMs={} message={}",
                    jobId,
                    stageBefore,
                    duration,
                    ex.message,
                    ex
                )

                throw ex
            }
        }

        return current
    }
}