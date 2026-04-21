package com.creatorcontenthub.application.port

interface VideoIngestionPort {
    fun ingest(url: String, jobId: String)
}