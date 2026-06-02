package com.creatorcontenthub.application.pipeline

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.infrastructure.runtime.RuntimeEvent
import com.creatorcontenthub.infrastructure.runtime.RuntimeEventBus
import org.slf4j.LoggerFactory

class JobProcessor(
    private val jobRepository: JobRepository,
    private val steps: List<PipelineStep>,
    private val runtimeEventBus: RuntimeEventBus
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

        runtimeEventBus.publish(
            RuntimeEvent(
                jobId = jobId,
                event = "resume_started",
                stage = initialState.stage.name,
                status = initialState.status.name,
                message = "Pipeline resumed"
            )
        )

        for (step in executableSteps) {

            val stageBefore = current.stage
            val startedAt = System.currentTimeMillis()

            logger.info(
                "event=stage_started jobId={} stage={}",
                jobId,
                stageBefore
            )

            runtimeEventBus.publish(
                RuntimeEvent(
                    jobId = jobId,
                    event = "stage_started",
                    stage = stageBefore.name,
                    status = current.status.name,
                    message = "Stage started"
                )
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

                runtimeEventBus.publish(
                    RuntimeEvent(
                        jobId = jobId,
                        event = "stage_changed",
                        stage = current.stage.name,
                        status = current.status.name,
                        message = "Pipeline advanced to ${current.stage.name}"
                    )
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

                runtimeEventBus.publish(
                    RuntimeEvent(
                        jobId = jobId,
                        event = "job_failed",
                        stage = stageBefore.name,
                        status = current.status.name,
                        message = ex.message ?: "Pipeline failed"
                    )
                )

                throw ex
            }
        }

        logger.info(
            "event=resume_completed jobId={} finalStage={}",
            jobId,
            current.stage
        )

        runtimeEventBus.publish(
            RuntimeEvent(
                jobId = jobId,
                event = "job_completed",
                stage = current.stage.name,
                status = current.status.name,
                message = "Pipeline completed"
            )
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
            JobStage.AUDIO_GENERATED,
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