package com.creatorcontenthub.application.port

interface VideoIngestionPort {
    suspend fun ingest(url: String, jobId: String): String
}