package com.creatorcontenthub.application.port

interface AudioGenerationPort {

    suspend fun generateAudio(
        videoPath: String,
        jobId: String
    ): String
}