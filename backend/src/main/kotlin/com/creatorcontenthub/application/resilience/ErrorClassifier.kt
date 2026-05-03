package com.creatorcontenthub.application.resilience

import com.creatorcontenthub.infrastructure.exception.ProcessExecutionException
import com.creatorcontenthub.domain.model.ErrorType
import java.io.IOException
import java.util.concurrent.TimeoutException

object ErrorClassifier {

    fun classify(
        throwable: Throwable? = null,
        exitCode: Int? = null,
        message: String? = null
    ): ErrorType {

        val msg = message ?: throwable?.message ?: ""

        // 1. Timeout explícito
        if (throwable is TimeoutException) {
            return ErrorType.TIMEOUT
        }

        // 2. Falha de dependência externa (rede, IO)
        if (throwable is IOException) {
            return ErrorType.DEPENDENCY_FAILURE
        }

        // 3. Heurística baseada em mensagem (CRÍTICO)
        if (
            msg.contains("Temporary failure", true) ||
            msg.contains("timed out", true) ||
            msg.contains("connection reset", true) ||
            msg.contains("network is unreachable", true) ||
            msg.contains("Failed to resolve", true) ||
            msg.contains("getaddrinfo failed", true)
        ) {
            return ErrorType.DEPENDENCY_FAILURE
        }

        // 4. ProcessExecutionException explícita
        if (throwable is ProcessExecutionException) {
            return if (throwable.exitCode != 0) {
                ErrorType.PROCESS_ERROR
            } else {
                ErrorType.UNKNOWN
            }
        }

        // 5. fallback baseado em exitCode
        if (exitCode != null && exitCode != 0) {
            return ErrorType.PROCESS_ERROR
        }

        // 6. fallback final
        return ErrorType.UNKNOWN
    }
}