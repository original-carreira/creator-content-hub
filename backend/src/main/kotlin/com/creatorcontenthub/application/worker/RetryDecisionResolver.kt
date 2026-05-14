package com.creatorcontenthub.application.worker

import com.creatorcontenthub.domain.model.RetryDecision

class RetryDecisionResolver {

    fun resolve(
        attempts: Int,
        exception: Exception
    ): RetryDecision {

        if (attempts >= 5) {
            return RetryDecision.DLQ
        }

        return when (exception) {

            is IllegalArgumentException ->
                RetryDecision.TERMINAL

            is IllegalStateException ->
                RetryDecision.TERMINAL

            else ->
                RetryDecision.RETRYABLE
        }
    }
}