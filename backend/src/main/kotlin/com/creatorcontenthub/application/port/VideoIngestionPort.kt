package com.creatorcontenthub.application.port

interface VideoIngestionPort {
    fun ingest(url: String, jobId: String): String // 🔥 retorna caminho do áudio
}