package com.creatorcontenthub.application.service

import com.creatorcontenthub.domain.model.JobState

class AssetResolver {

    fun resolve(
        assetId: String,
        job: JobState
    ): AssetResolution? {

        return when (assetId) {

            "video" -> {
                val videoPath = job.videoPath
                    ?: return null

                AssetResolution(
                    assetId = "video",
                    filePath = videoPath,
                    fileName = "video.mp4"
                )
            }

            "mp3" -> {
                val audioPath = job.audioPath
                    ?: return null

                AssetResolution(
                    assetId = "mp3",
                    filePath = audioPath,
                    fileName = "audio.mp3"
                )
            }

            else -> null
        }
    }
}