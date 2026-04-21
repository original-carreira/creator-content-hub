package com.creatorcontenthub.infrastructure.store

import com.creatorcontenthub.domain.model.JobStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * TTL baseado exclusivamente em createdAt.
 *
 * IMPORTANTE:
 * - Jobs podem expirar mesmo ainda em execução.
 * - Esse comportamento é intencional para manter simplicidade.
 * - Não há extensão de TTL após conclusão.
 */
private const val TTL_MILLIS = 10 * 60 * 1000 // 10 minutos

class InMemoryJobStatusStore {

    data class JobEntry(
        val status: JobStatus,
        val createdAt: Long
    )

    private val store = ConcurrentHashMap<String, JobEntry>()

    fun create(jobId: String) {
        store[jobId] = JobEntry(
            status = JobStatus.PROCESSING,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Atualiza apenas o status, SEM alterar o createdAt (TTL fixo)
     */
    fun update(jobId: String, status: JobStatus) {
        val existing = store[jobId] ?: return

        store[jobId] = JobEntry(
            status = status,
            createdAt = existing.createdAt // mantém TTL original
        )
    }

    fun get(jobId: String): JobStatus? {
        val entry = store[jobId] ?: return null

        // cleanup passivo
        if (isExpired(entry)) {
            store.remove(jobId)
            return null
        }

        return entry.status
    }

    fun exists(jobId: String): Boolean {
        val entry = store[jobId] ?: return false

        // cleanup passivo
        if (isExpired(entry)) {
            store.remove(jobId)
            return false
        }

        return true
    }

    private fun isExpired(entry: JobEntry): Boolean {
        val now = System.currentTimeMillis()
        return now - entry.createdAt > TTL_MILLIS
    }

    /**
     * Cleanup ativo (scheduler-safe)
     * - sem removeIf
     * - thread-safe com ConcurrentHashMap
     */
    fun cleanup() {
        val now = System.currentTimeMillis()

        store.forEach { (jobId, entry) ->
            if (now - entry.createdAt > TTL_MILLIS) {
                store.remove(jobId)
            }
        }
    }
}