package com.creatorcontenthub.domain.model

enum class JobStage {
    UNKNOWN,
    CREATED,
    DOWNLOADED,
    AUDIO_GENERATED,
    TRANSCRIBED,
    SUMMARIZED,
    COMPLETED,
    FAILED
}