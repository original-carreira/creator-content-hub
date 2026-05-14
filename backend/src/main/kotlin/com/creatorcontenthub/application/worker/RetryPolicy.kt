package com.creatorcontenthub.application.worker

class RetryPolicy {

    fun calculateDelayMillis(
        attempt: Int
    ): Long {

        return when (attempt) {
            1 -> 5_000L
            2 -> 15_000L
            3 -> 60_000L
            else -> 300_000L
        }
    }
}