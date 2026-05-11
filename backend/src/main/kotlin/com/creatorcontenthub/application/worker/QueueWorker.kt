package com.creatorcontenthub.application.worker

import com.creatorcontenthub.application.pipeline.JobProcessor
import com.creatorcontenthub.application.port.JobQueueRepository
import com.creatorcontenthub.application.port.JobRepository
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class QueueWorker(
    private val queueRepository: JobQueueRepository,
    private val jobRepository: JobRepository,
    private val jobProcessor: JobProcessor
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun start() {

        logger.info("event=worker_started")

        while (true) {

            try {

                val item = queueRepository.findNextPending()

                if (item == null) {
                    delay(2000)
                    continue
                }

                val queueId = item.id
                    ?: throw IllegalStateException("Queue item without id")

                val startedAt = System.currentTimeMillis()

                queueRepository.markProcessing(
                    queueId = queueId,
                    startedAt = startedAt
                )

                logger.info(
                    "event=worker_claimed_job queueId={} jobId={}",
                    queueId,
                    item.jobId
                )

                val state = jobRepository.findById(item.jobId)
                    ?: throw IllegalStateException(
                        "Job not found: ${item.jobId}"
                    )

                val result = jobProcessor.process(
                    jobId = item.jobId,
                    initialState = state
                )

                val completedAt = System.currentTimeMillis()

                queueRepository.markCompleted(
                    queueId = queueId,
                    completedAt = completedAt
                )

                logger.info(
                    "event=worker_completed_job queueId={} jobId={} stage={}",
                    queueId,
                    item.jobId,
                    result.stage
                )

            } catch (ex: Exception) {

                logger.error(
                    "event=worker_failed_job message={}",
                    ex.message,
                    ex
                )

                delay(2000)
            }
        }
    }
}