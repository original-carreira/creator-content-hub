package com.creatorcontenthub.infrastructure.exception

class ExternalServiceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)