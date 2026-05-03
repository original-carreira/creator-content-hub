package com.creatorcontenthub.infrastructure.resilience

import java.util.concurrent.Semaphore

object WhisperConcurrencyLimiter {

    private val semaphore = Semaphore(1)

    fun <T> withPermitBlocking(block: () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }
}