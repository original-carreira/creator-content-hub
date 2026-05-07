package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStage
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

        val executableSteps = steps.filter { step ->
            shouldExecuteStep(
                currentStage = initialState.stage,
                stepStage = step.supportedStage
            )
        }

        logger.info(
            "event=resume_started jobId={} resumedFrom={} executableSteps={}",
            jobId,
            initialState.stage,
            executableSteps.map { step ->
                step::class.java.simpleName
            }
        )

        for (step in executableSteps) {

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
                    "event=resume_failed jobId={} stage={} durationMs={} message={}",
                    jobId,
                    stageBefore,
                    duration,
                    ex.message,
                    ex
                )

                throw ex
            }
        }

        logger.info(
            "event=resume_completed jobId={} finalStage={}",
            jobId,
            current.stage
        )

        return current
    }

    private fun shouldExecuteStep(
        currentStage: JobStage,
        stepStage: JobStage
    ): Boolean {

        val order = listOf(
            JobStage.CREATED,
            JobStage.DOWNLOADED,
            JobStage.TRANSCRIBED,
            JobStage.SUMMARIZED
        )

        val currentIndex = order.indexOf(currentStage)
        val stepIndex = order.indexOf(stepStage)

        if (currentIndex == -1 || stepIndex == -1) {
            return false
        }

        return stepIndex >= currentIndex
    }
}