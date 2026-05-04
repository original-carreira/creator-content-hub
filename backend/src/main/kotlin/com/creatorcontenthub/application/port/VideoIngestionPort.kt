package com.creatorcontenthub.application.port

import com.creatorcontenthub.application.dto.IngestionResult

interface VideoIngestionPort {
    suspend fun ingest(url: String, jobId: String): IngestionResult
}