package com.creatorcontenthub.application.port

import com.creatorcontenthub.domain.model.Asset

interface AssetRepository {

    fun create(
        asset: Asset
    )

    fun update(
        asset: Asset
    )

    fun delete(
        assetId: String
    )

    fun findById(
        assetId: String
    ): Asset?

    fun findByJobId(
        jobId: String
    ): List<Asset>

    fun findByStoragePath(
        storagePath: String
    ): Asset?
}