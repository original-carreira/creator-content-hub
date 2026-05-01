package com.creatorcontenthub.infrastructure.cleanup

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FileCleanupService(
    private val baseDir: String,
    private val ttlHours: Long = 24
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        scheduler.scheduleAtFixedRate(
            { safeCleanup() },
            5,   // delay inicial
            60,  // intervalo
            TimeUnit.MINUTES
        )
    }

    private fun safeCleanup() {
        try {
            cleanup()
        } catch (ex: Exception) {
            logger.error("event=cleanup_error message={}", ex.message)
        }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val ttlMillis = ttlHours * 60 * 60 * 1000

        val dirs = listOf(
            File("$baseDir/audio"),
            File("$baseDir/transcription"),
            File("$baseDir/temp")
        )

        dirs.forEach { dir ->
            if (!dir.exists()) return@forEach

            dir.listFiles()?.forEach { file ->
                val age = now - file.lastModified()

                if (age > ttlMillis) {
                    if (file.delete()) {
                        logger.info("event=file_deleted path={}", file.absolutePath)
                    }
                }
            }
        }
    }
}