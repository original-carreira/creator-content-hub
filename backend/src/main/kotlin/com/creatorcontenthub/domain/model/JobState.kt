package com.creatorcontenthub.domain.model

data class JobState(
    val status: JobStatus,
    val createdAt: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,

    val errorType: ErrorType? = null,
    val errorMessage: String? = null,
    val transcription: String? = null,
    val transcriptionCompletedAt: Long? = null
) {

    init {

        // 🔒 INVARIANTE 1 — FAILED precisa de erro
        if (status == JobStatus.FAILED) {
            require(errorType != null) {
                "FAILED state must contain errorType"
            }
        }

        // 🔒 INVARIANTE 2 — DONE não pode ter erro
        if (status == JobStatus.DONE) {
            require(errorType == null) {
                "DONE state cannot contain errorType"
            }
        }

        // 🔒 INVARIANTE 3 — consistência temporal básica
        require(startedAt >= createdAt) {
            "startedAt must be >= createdAt"
        }

        // 🔒 INVARIANTE 4 — finishedAt coerente
        if (finishedAt != null) {
            require(finishedAt >= startedAt) {
                "finishedAt must be >= startedAt"
            }
        }

        // 🔒 INVARIANTE 5 — estados finais devem ter finishedAt
        if (status == JobStatus.DONE || status == JobStatus.FAILED) {
            require(finishedAt != null) {
                "Final states must contain finishedAt"
            }
        }

        // 🔒 INVARIANTE 6 — estados não finais não devem ter finishedAt
        if (status != JobStatus.DONE && status != JobStatus.FAILED) {
            require(finishedAt == null) {
                "Non-final states cannot contain finishedAt"
            }
        }

        // 🔥 NOVOS — TRANSCRIÇÃO

        // 🔒 INVARIANTE 7 — DONE deve ter transcrição
        if (status == JobStatus.DONE) {
            require(!transcription.isNullOrBlank()) {
                "DONE state must contain transcription"
            }
        }

        // 🔒 INVARIANTE 8 — FAILED não pode ter transcrição
        if (status == JobStatus.FAILED) {
            require(transcription == null) {
                "FAILED state cannot contain transcription"
            }
        }

        // 🔒 INVARIANTE 9 — transcriptionCompletedAt coerente
        if (transcription != null) {
            require(transcriptionCompletedAt != null) {
                "transcriptionCompletedAt must be present when transcription exists"
            }

            require(transcriptionCompletedAt >= startedAt) {
                "transcriptionCompletedAt must be >= startedAt"
            }
        }

        // 🔒 INVARIANTE 10 — não pode existir sem transcription
        if (transcription == null) {
            require(transcriptionCompletedAt == null) {
                "transcriptionCompletedAt cannot exist without transcription"
            }
        }
    }
}