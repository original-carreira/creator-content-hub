package com.creatorcontenthub.domain.exception

class TooManyRequestsException(
    message: String = "Too many requests. Please try again later."
) : RuntimeException(message)