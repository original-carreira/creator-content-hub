package com.creatorcontenthub.domain.model

import java.io.IOException
import java.util.concurrent.TimeoutException

object ErrorClassifier {

    fun classify(
        throwable: Throwable? = null,
        exitCode: Int? = null
    ): ErrorType {

        // 1. Timeout explícito
        if (throwable is TimeoutException) {
            return ErrorType.TIMEOUT
        }

        // 2. Falha de dependência externa
        if (throwable is IOException) {
            return ErrorType.DEPENDENCY_FAILURE
        }

        // 3. Processo retornou erro
        if (exitCode != null && exitCode != 0) {
            return ErrorType.PROCESS_ERROR
        }

        // 4. Fallback
        return ErrorType.UNKNOWN
    }
}