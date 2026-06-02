package com.creatorcontenthub.infrastructure.storage

import java.io.File

class FileStorageService(
    private val baseDir: String = "data/jobs"
) {

    fun saveTranscription(jobId: String, content: String): String {
        val file = resolveFile(jobId, "transcription.txt")
        file.writeText(content)
        return file.absolutePath
    }

    fun saveSummary(jobId: String, content: String): String {
        val file = resolveFile(jobId, "summary.txt")
        file.writeText(content)
        return file.absolutePath
    }

    fun saveAudio(jobId: String, sourcePath: String?): String? {
        if (sourcePath == null) return null

        val source = File(sourcePath)
        if (!source.exists()) return null

        val target = resolveFile(jobId, "audio.mp3")

        source.copyTo(target, overwrite = true)

        return target.absolutePath
    }

    private fun resolveFile(jobId: String, fileName: String): File {
        val dir = File("$baseDir/$jobId")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return File(dir, fileName)
    }

    private fun resolveAssetFile(jobId: String, assetType: String, fileName: String): File {

        val dir = File("$baseDir/$jobId/assets/$assetType")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return File(dir, fileName)
    }

    fun saveAsset(jobId: String, assetType: String, sourcePath: String, fileName: String): String {

        val source = File(sourcePath)

        require(source.exists()) {
            "Asset source file does not exist: $sourcePath"
        }

        val target = resolveAssetFile(
            jobId = jobId,
            assetType = assetType,
            fileName = fileName
        )

        source.copyTo(
            target,
            overwrite = true
        )

        return target.absolutePath
    }
}