package com.creatorcontenthub.application.service

import com.creatorcontenthub.application.port.AssetRepository
import com.creatorcontenthub.domain.model.Asset
import com.creatorcontenthub.domain.model.AssetType
import java.util.UUID

class AssetRegistrationService(
    private val assetRepository: AssetRepository
) {

    fun register(
        jobId: String,
        assetType: AssetType,
        storagePath: String
    ): Asset {

        require(jobId.isNotBlank()) {
            "jobId must not be blank"
        }

        require(storagePath.isNotBlank()) {
            "storagePath must not be blank"
        }

        val existing =
            assetRepository.findByStoragePath(
                storagePath
            )

        if (existing != null) {
            return existing
        }

        val asset =
            Asset(
                assetId = UUID.randomUUID().toString(),
                jobId = jobId,
                assetType = assetType,
                storagePath = storagePath,
                createdAt = System.currentTimeMillis()
            )

        assetRepository.create(asset)

        return asset
    }
}