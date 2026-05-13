package com.creatorcontenthub.application.worker

import com.creatorcontenthub.application.port.JobQueueRepository
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class OrphanRecoveryWorker(
    private val queueRepository: JobQueueRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val heartbeatTimeoutMs = 60_000L
    private val scanIntervalMs = 15_000L
    private val maxAttempts = 5

    suspend fun start() {

        logger.info(
            "event=orphan_recovery_worker_started timeoutMs={} scanIntervalMs={} maxAttempts={}",
            heartbeatTimeoutMs,
            scanIntervalMs,
            maxAttempts
        )

        while (true) {

            try {

                val timeoutBefore =
                    System.currentTimeMillis() - heartbeatTimeoutMs

                val expiredJobs =
                    queueRepository.findExpiredProcessingJobs(
                        heartbeatTimeoutBefore = timeoutBefore
                    )

                for (item in expiredJobs) {

                    logger.warn(
                        "event=orphan_detected queueId={} jobId={} workerId={} attempts={} lastHeartbeatAt={} timeoutMs={}",
                        item.id,
                        item.jobId,
                        item.claimedBy,
                        item.attempts,
                        item.lastHeartbeatAt,
                        heartbeatTimeoutMs
                    )

                    if (item.attempts >= maxAttempts) {

                        val failed =
                            queueRepository.markFailedMaxAttempts(
                                queueId = item.id!!,
                                completedAt = System.currentTimeMillis(),
                                errorMessage = "Maximum retry attempts exceeded"
                            )

                        if (failed) {

                            logger.error(
                                "event=max_attempts_reached queueId={} jobId={} attempts={}",
                                item.id,
                                item.jobId,
                                item.attempts
                            )
                        }

                        continue
                    }

                    val requeued =
                        queueRepository.requeueOrphanedJob(
                            queueId = item.id!!
                        )

                    if (requeued) {

                        logger.warn(
                            "event=orphan_requeued queueId={} jobId={} attempts={}",
                            item.id,
                            item.jobId,
                            item.attempts
                        )
                    }
                }

            } catch (ex: Exception) {

                logger.error(
                    "event=orphan_recovery_failure message={}",
                    ex.message,
                    ex
                )
            }

            delay(scanIntervalMs)
        }
    }
}
