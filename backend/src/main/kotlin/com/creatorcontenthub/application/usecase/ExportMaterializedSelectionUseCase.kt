package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.AssetRepository
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.port.VideoClipExportPort
import com.creatorcontenthub.domain.model.AssetType
import java.io.File

class ExportMaterializedSelectionUseCase(
    private val jobRepository: JobRepository,
    private val assetRepository: AssetRepository,
    private val videoClipExportPort: VideoClipExportPort
) {

    suspend fun execute(
        jobId: String,
        assetId: String,
        startTime: Double,
        endTime: Double
    ): File {

        if (
            !startTime.isFinite() ||
            !endTime.isFinite() ||
            startTime < 0.0 ||
            endTime <= startTime
        ) {
            throw InvalidVideoClipRangeException()
        }

        if (jobRepository.findById(jobId) == null) {
            throw ExportJobNotFoundException()
        }

        val asset =
            assetRepository.findById(assetId)
                ?: throw ExportVideoAssetNotFoundException()

        if (asset.jobId != jobId) {
            throw ExportVideoAssetNotFoundException()
        }

        if (asset.assetType != AssetType.VIDEO) {
            throw ExportAssetIsNotVideoException()
        }

        val sourceVideo = File(asset.storagePath)

        if (
            !sourceVideo.isFile ||
            !sourceVideo.canRead()
        ) {
            throw ExportVideoSourceUnavailableException()
        }

        return videoClipExportPort.export(
            sourceVideo = sourceVideo,
            startTime = startTime,
            endTime = endTime
        )
    }
}

class ExportJobNotFoundException : RuntimeException()

class ExportVideoAssetNotFoundException : RuntimeException()

class ExportAssetIsNotVideoException : RuntimeException()

class ExportVideoSourceUnavailableException : RuntimeException()

class InvalidVideoClipRangeException : RuntimeException()

class VideoClipOutsideSourceException : RuntimeException()

class VideoClipExportProcessingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
