package com.creatorcontenthub.infrastructure.concurrency

import com.creatorcontenthub.application.port.ConcurrencyControlPort
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class SemaphoreConcurrencyController(
    maxConcurrentJobs: Int
) : ConcurrencyControlPort {

    // FAIR (FIFO)
    private val semaphore = Semaphore(maxConcurrentJobs, true)

    /**
     * ⚠️ BLOQUEANTE
     * NÃO usar em fluxo HTTP.
     * Apenas para processamento interno controlado.
     */
    override fun acquire() {
        try {
            semaphore.acquire()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Thread interrupted while acquiring semaphore", ex)
        }
    }

    override fun release() {
        semaphore.release()
    }

    /**
     * Tentativa de aquisição NÃO BLOQUEANTE (para HTTP / backpressure).
     *
     * Regras:
     * - timeout deve ser baixo (0–50ms)
     * - evita fila invisível
     * - permite rejeição imediata (HTTP 429)
     */
    override fun tryAcquire(timeoutMillis: Long): Boolean {
        require(timeoutMillis in 0..50) {
            "Timeout too high for HTTP backpressure: $timeoutMillis ms"
        }

        return try {
            semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * Versão explícita para tentativa imediata (sem espera)
     */
    fun tryAcquireImmediate(): Boolean {
        return semaphore.tryAcquire()
    }
}