package com.creatorcontenthub.domain.model

data class Asset(
    val assetId: String,
    val jobId: String,
    val assetType: AssetType,
    val storagePath: String,
    val createdAt: Long
) {

    init {

        require(assetId.isNotBlank()) {
            "assetId must not be blank"
        }

        require(jobId.isNotBlank()) {
            "jobId must not be blank"
        }

        require(storagePath.isNotBlank()) {
            "storagePath must not be blank"
        }
    }
}
