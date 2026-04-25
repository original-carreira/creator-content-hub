package com.creatorcontenthub.infrastructure.resilience

import java.util.concurrent.Semaphore

object WhisperConcurrencyLimiter {

    // 🔒 1 transcription por vez (CPU-bound)
    private val semaphore = Semaphore(1)

    fun acquire() {
        semaphore.acquire()
    }

    fun release() {
        semaphore.release()
    }
}