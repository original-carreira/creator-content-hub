package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.AssetRepository
import com.creatorcontenthub.domain.model.Asset
import com.creatorcontenthub.domain.model.AssetType
import javax.sql.DataSource

class PostgresAssetRepository(
    private val dataSource: DataSource
) : AssetRepository {

    override fun create(
        asset: Asset
    ) {

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
                INSERT INTO assets (
                    asset_id,
                    job_id,
                    asset_type,
                    storage_path,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    asset.assetId
                )

                statement.setString(
                    2,
                    asset.jobId
                )

                statement.setString(
                    3,
                    asset.assetType.name
                )

                statement.setString(
                    4,
                    asset.storagePath
                )

                statement.setLong(
                    5,
                    asset.createdAt
                )

                statement.executeUpdate()
            }
        }
    }

    override fun update(
        asset: Asset
    ) {

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
                UPDATE assets
                SET
                    job_id = ?,
                    asset_type = ?,
                    storage_path = ?
                WHERE asset_id = ?
                """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    asset.jobId
                )

                statement.setString(
                    2,
                    asset.assetType.name
                )

                statement.setString(
                    3,
                    asset.storagePath
                )

                statement.setString(
                    4,
                    asset.assetId
                )

                statement.executeUpdate()
            }
        }
    }

    override fun delete(
        assetId: String
    ) {

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
                DELETE FROM assets
                WHERE asset_id = ?
                """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    assetId
                )

                statement.executeUpdate()
            }
        }
    }

    override fun findById(
        assetId: String
    ): Asset? {

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
                SELECT
                    asset_id,
                    job_id,
                    asset_type,
                    storage_path,
                    created_at
                FROM assets
                WHERE asset_id = ?
                """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    assetId
                )

                statement.executeQuery().use { rs ->

                    if (!rs.next()) {
                        return null
                    }

                    return mapAsset(rs)
                }
            }
        }
    }

    override fun findByJobId(
        jobId: String
    ): List<Asset> {

        val assets =
            mutableListOf<Asset>()

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
                SELECT
                    asset_id,
                    job_id,
                    asset_type,
                    storage_path,
                    created_at
                FROM assets
                WHERE job_id = ?
                ORDER BY created_at ASC
                """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    jobId
                )

                statement.executeQuery().use { rs ->

                    while (rs.next()) {

                        assets.add(
                            mapAsset(rs)
                        )
                    }
                }
            }
        }

        return assets
    }

    override fun findByStoragePath(
        storagePath: String
    ): Asset? {

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
                SELECT
                    asset_id,
                    job_id,
                    asset_type,
                    storage_path,
                    created_at
                FROM assets
                WHERE storage_path = ?
                """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    storagePath
                )

                statement.executeQuery().use { rs ->

                    if (!rs.next()) {
                        return null
                    }

                    return mapAsset(rs)
                }
            }
        }
    }

    private fun mapAsset(
        rs: java.sql.ResultSet
    ): Asset {

        return Asset(
            assetId =
                rs.getString(
                    "asset_id"
                ),

            jobId =
                rs.getString(
                    "job_id"
                ),

            assetType =
                AssetType.valueOf(
                    rs.getString(
                        "asset_type"
                    )
                ),

            storagePath =
                rs.getString(
                    "storage_path"
                ),

            createdAt =
                rs.getLong(
                    "created_at"
                )
        )
    }
}