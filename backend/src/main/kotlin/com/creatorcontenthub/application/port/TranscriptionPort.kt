package com.creatorcontenthub.application.port

import com.creatorcontenthub.application.dto.TranscriptionResult

interface TranscriptionPort {
    fun transcribe(audioPath: String): TranscriptionResult
}