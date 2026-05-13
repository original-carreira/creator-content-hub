package com.creatorcontenthub.application.worker

import com.creatorcontenthub.application.pipeline.JobProcessor
import com.creatorcontenthub.application.port.JobQueueRepository
import com.creatorcontenthub.application.port.JobRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val heartbeatIntervalMs = 15_000L

    suspend fun start() {

        logger.info(
            "event=worker_started workerId={}",
            workerId
        )

        while (true) {

            var queueId: Long? = null

            try {

                val startedAt = System.currentTimeMillis()

                logger.debug(
                    "event=queue_claim_attempt workerId={}",
                    workerId
                )

                val item = queueRepository.claimNextPending(
                    workerId = workerId,
                    startedAt = startedAt
                )

                if (item == null) {

                    logger.debug(
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

                logger.debug(
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

                val result = coroutineScope {

                    val heartbeatJob: Job = launch {

                        while (isActive) {

                            delay(heartbeatIntervalMs)

                            val updated = queueRepository.updateHeartbeat(
                                queueId = queueId,
                                workerId = workerId,
                                heartbeatAt = System.currentTimeMillis()
                            )

                            if (!updated) {

                                logger.warn(
                                    "event=heartbeat_rejected workerId={} queueId={} jobId={}",
                                    workerId,
                                    queueId,
                                    item.jobId
                                )

                                break
                            }

                            logger.info(
                                "event=heartbeat_updated workerId={} queueId={} jobId={}",
                                workerId,
                                queueId,
                                item.jobId
                            )
                        }
                    }

                    try {

                        jobProcessor.process(
                            jobId = item.jobId,
                            initialState = state
                        )

                    } finally {

                        heartbeatJob.cancelAndJoin()
                    }
                }

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