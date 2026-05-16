package com.creatorcontenthub.application.worker

import com.creatorcontenthub.application.pipeline.JobProcessor
import com.creatorcontenthub.application.port.DeadLetterQueueRepository
import com.creatorcontenthub.application.port.JobQueueRepository
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.RetryDecision
import com.creatorcontenthub.infrastructure.runtime.RuntimeEvent
import com.creatorcontenthub.infrastructure.runtime.RuntimeEventBus
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
    private val deadLetterQueueRepository: DeadLetterQueueRepository,
    private val jobRepository: JobRepository,
    private val jobProcessor: JobProcessor,
    private val retryPolicy: RetryPolicy,
    private val retryDecisionResolver: RetryDecisionResolver,
    private val runtimeEventBus: RuntimeEventBus
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
            var item: com.creatorcontenthub.domain.model.JobQueueItem? = null

            try {

                val startedAt = System.currentTimeMillis()

                logger.debug(
                    "event=queue_claim_attempt workerId={}",
                    workerId
                )

                item = queueRepository.claimNextPending(
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

                                runtimeEventBus.publish(
                                    RuntimeEvent(
                                        jobId = item.jobId,
                                        event = "heartbeat_timeout",
                                        message = "Worker heartbeat rejected"
                                    )
                                )

                                break
                            }

                            logger.debug(
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

                    val currentItem = item ?: return@let

                    val retryDecision = retryDecisionResolver.resolve(
                        attempts = currentItem.attempts,
                        exception = ex
                    )

                    when (retryDecision) {

                        RetryDecision.RETRYABLE -> {

                            val delayMillis = retryPolicy.calculateDelayMillis(
                                currentItem.attempts
                            )

                            val retryAt = System.currentTimeMillis() + delayMillis

                            queueRepository.scheduleRetry(
                                queueId = it,
                                retryAt = retryAt,
                                errorMessage = ex.message
                            )

                            logger.warn(
                                "event=retry_scheduled workerId={} queueId={} jobId={} retryAt={} delayMillis={} attempts={}",
                                workerId,
                                it,
                                currentItem.jobId,
                                retryAt,
                                delayMillis,
                                currentItem.attempts
                            )

                            runtimeEventBus.publish(
                                RuntimeEvent(
                                    jobId = currentItem.jobId,
                                    event = "retry_scheduled",
                                    message = "Retry scheduled"
                                )
                            )
                        }

                        RetryDecision.TERMINAL -> {

                            queueRepository.markFailed(
                                queueId = it,
                                completedAt = System.currentTimeMillis(),
                                errorMessage = ex.message
                            )

                            logger.error(
                                "event=terminal_failure workerId={} queueId={} jobId={} attempts={}",
                                workerId,
                                it,
                                currentItem.jobId,
                                currentItem.attempts
                            )

                            runtimeEventBus.publish(
                                RuntimeEvent(
                                    jobId = currentItem.jobId,
                                    event = "job_failed",
                                    message = "Terminal job failure"
                                )
                            )
                        }

                        RetryDecision.DLQ -> {

                            deadLetterQueueRepository.insert(
                                com.creatorcontenthub.domain.model.DeadLetterQueueItem(
                                    jobId = currentItem.jobId,
                                    queueId = currentItem.id,
                                    stage = "QUEUE_WORKER",
                                    errorMessage = ex.stackTraceToString(),
                                    failedAt = System.currentTimeMillis(),
                                    attempts = currentItem.attempts,
                                    workerId = workerId,
                                    payloadSnapshot = null
                                )
                            )

                            queueRepository.markAsDeadLetter(
                                queueId = it,
                                completedAt = System.currentTimeMillis(),
                                errorMessage = ex.message
                            )

                            logger.error(
                                "event=job_sent_to_dlq workerId={} queueId={} jobId={} attempts={}",
                                workerId,
                                it,
                                currentItem.jobId,
                                currentItem.attempts
                            )

                            runtimeEventBus.publish(
                                RuntimeEvent(
                                    jobId = currentItem.jobId,
                                    event = "dlq_transition",
                                    message = "Job moved to dead letter queue"
                                )
                            )
                        }
                    }
                }

                delay(2000)
            }
        }
    }
}