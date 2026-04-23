package com.creatorcontenthub.domain.exception

class TranscriptionTimeoutException(
    message: String = "Transcription timeout"
) : RuntimeException(message)