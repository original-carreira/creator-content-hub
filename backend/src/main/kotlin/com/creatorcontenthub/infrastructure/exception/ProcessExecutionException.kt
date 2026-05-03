package com.creatorcontenthub.infrastructure.exception

class ProcessExecutionException(
    val exitCode: Int,
    message: String
) : RuntimeException(message)