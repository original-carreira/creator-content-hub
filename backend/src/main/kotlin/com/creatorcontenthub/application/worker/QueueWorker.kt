package com.creatorcontenthub.application.worker

import com.creatorcontenthub.application.pipeline.JobProcessor
import com.creatorcontenthub.application.port.JobQueueRepository
import com.creatorcontenthub.application.port.JobRepository
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.UUID

class QueueWorker(
    private val queueRepository: JobQueueRepository,
    private val jobRepository: JobRepository,
    private val jobProcessor: JobProcessor
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val workerId = UUID.randomUUID().toString()

    suspend fun start() {

        logger.info(
            "event=worker_started workerId={}",
            workerId
        )

        while (true) {

            var queueId: Long? = null

            try {

                val startedAt = System.currentTimeMillis()

                logger.info(
                    "event=queue_claim_attempt workerId={}",
                    workerId
                )

                val item = queueRepository.claimNextPending(
                    workerId = workerId,
                    startedAt = startedAt
                )

                if (item == null) {

                    logger.info(
                        "event=queue_claim_empty workerId={}",
                        workerId
                    )

                    delay(2000)
                    continue
                }

                queueId = item.id
                    ?: throw IllegalStateException(
                        "Queue item without id"
                    )

                logger.info(
                    "event=queue_claim_success workerId={} queueId={} jobId={} attempts={}",
                    workerId,
                    queueId,
                    item.jobId,
                    item.attempts
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
                    "event=worker_completed_job workerId={} queueId={} jobId={} stage={}",
                    workerId,
                    queueId,
                    item.jobId,
                    result.stage
                )

            } catch (ex: Exception) {

                logger.error(
                    "event=worker_failed_job workerId={} queueId={} message={}",
                    workerId,
                    queueId,
                    ex.message,
                    ex
                )

                queueId?.let {

                    queueRepository.markFailed(
                        queueId = it,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = ex.message
                    )
                }

                delay(2000)
            }
        }
    }
}