package com.creatorcontenthub.infrastructure.store

import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import java.util.concurrent.ConcurrentHashMap

private const val TTL_MILLIS = 10 * 60 * 1000 // 10 minutos

class InMemoryJobStatusStore {

    private val store = ConcurrentHashMap<String, JobState>()

    fun create(jobId: String) {
        val now = System.currentTimeMillis()

        store[jobId] = JobState(
            status = JobStatus.PROCESSING,
            createdAt = now,
            startedAt = now,
            finishedAt = null,
            errorType = null,
            errorMessage = null,
            transcription = null
        )
    }

    fun markDone(jobId: String, transcription: String) {
        store.computeIfPresent(jobId) { _, current ->
            val now = maxOf(System.currentTimeMillis(), current.startedAt)

            current.copy(
                status = JobStatus.DONE,
                finishedAt = now,
                transcription = transcription,
                transcriptionCompletedAt = now
            )
        }
    }

    fun markFailed(
        jobId: String,
        errorType: ErrorType,
        errorMessage: String
    ) {
        store.computeIfPresent(jobId) { _, current ->
            val now = maxOf(System.currentTimeMillis(), current.startedAt)

            current.copy(
                status = JobStatus.FAILED,
                finishedAt = now,
                errorType = errorType,
                errorMessage = errorMessage,
                transcription = null,
                transcriptionCompletedAt = null // ✔ aqui é o lugar correto
            )
        }
    }

    @Deprecated("Use markFailed(jobId, errorType, errorMessage)")
    fun markFailed(jobId: String, error: String) {
        markFailed(jobId, ErrorType.UNKNOWN, error)
    }

    fun get(jobId: String): JobState? {
        val state = store[jobId] ?: return null

        if (isExpired(state)) {
            store.remove(jobId)
            return null
        }

        return state
    }

    fun exists(jobId: String): Boolean {
        val state = store[jobId] ?: return false

        if (isExpired(state)) {
            store.remove(jobId)
            return false
        }

        return true
    }

    private fun isExpired(state: JobState): Boolean {
        val now = System.currentTimeMillis()
        val referenceTime = state.finishedAt ?: state.startedAt
        return now - referenceTime > TTL_MILLIS
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        val iterator = store.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val state = entry.value
            val referenceTime = state.finishedAt ?: state.startedAt

            if (now - referenceTime > TTL_MILLIS) {
                iterator.remove()
            }
        }
    }
}