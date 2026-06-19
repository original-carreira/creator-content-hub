package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.MediaNavigationContextRepository
import com.creatorcontenthub.domain.model.MediaNavigationContext
import com.creatorcontenthub.domain.model.MediaNavigationContextVersion
import com.creatorcontenthub.domain.model.MediaRange
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import javax.sql.DataSource

class PostgresMediaNavigationContextRepository(
    private val dataSource: DataSource
) : MediaNavigationContextRepository {

    private val json = Json

    override fun create(
        contextId: String,
        context: MediaNavigationContext
    ) {

        dataSource.connection.use { connection ->

            connection.autoCommit = false

            try {

                insertVersion(
                    connection = connection,
                    version = context.activeVersion
                )

                insertContext(
                    connection = connection,
                    context = context
                )

                connection.commit()

            } catch (ex: Exception) {

                connection.rollback()
                throw ex
            }
        }
    }

    override fun update(
        contextId: String,
        context: MediaNavigationContext
    ) {

        dataSource.connection.use { connection ->

            connection.autoCommit = false

            try {

                insertVersion(
                    connection = connection,
                    version = context.activeVersion
                )

                connection.prepareStatement(
                    """
                UPDATE media_navigation_contexts
                SET
                    asset_id = ?,
                    name = ?,
                    active_version_id = ?
                WHERE context_id = ?
                """.trimIndent()
                ).use { statement ->

                    statement.setString(
                        1,
                        context.assetId
                    )

                    statement.setString(
                        2,
                        context.name
                    )

                    statement.setString(
                        3,
                        context.activeVersion.versionId
                    )

                    statement.setString(
                        4,
                        context.contextId
                    )

                    statement.executeUpdate()
                }

                connection.commit()

            } catch (ex: Exception) {

                connection.rollback()
                throw ex
            }
        }
    }

    override fun delete(
        contextId: String
    ) {

        dataSource.connection.use { connection ->

            connection.autoCommit = false

            try {

                connection.prepareStatement(
                    """
                DELETE FROM media_navigation_context_versions
                WHERE context_id = ?
                """.trimIndent()
                ).use { statement ->

                    statement.setString(
                        1,
                        contextId
                    )

                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                DELETE FROM media_navigation_contexts
                WHERE context_id = ?
                """.trimIndent()
                ).use { statement ->

                    statement.setString(
                        1,
                        contextId
                    )

                    statement.executeUpdate()
                }

                connection.commit()

            } catch (ex: Exception) {

                connection.rollback()
                throw ex
            }
        }
    }

    override fun findById(
        contextId: String
    ): MediaNavigationContext? {

        dataSource.connection.use { connection ->

            connection.prepareStatement(
                """
            SELECT
                context_id,
                asset_id,
                name,
                created_at,
                active_version_id
            FROM media_navigation_contexts
            WHERE context_id = ?
            """.trimIndent()
            ).use { statement ->

                statement.setString(
                    1,
                    contextId
                )

                statement.executeQuery().use { resultSet ->

                    if (!resultSet.next()) {
                        return null
                    }

                    val activeVersionId =
                        resultSet.getString(
                            "active_version_id"
                        )

                    val activeVersion =
                        findVersionById(
                            connection = connection,
                            versionId = activeVersionId
                        ) ?: return null

                    return MediaNavigationContext(
                        contextId =
                            resultSet.getString(
                                "context_id"
                            ),
                        assetId =
                            resultSet.getString(
                                "asset_id"
                            ),
                        name =
                            resultSet.getString(
                                "name"
                            ),
                        createdAt =
                            resultSet.getLong(
                                "created_at"
                            ),
                        activeVersion =
                            activeVersion
                    )
                }
            }
        }
    }

    private fun insertContext(
        connection: Connection,
        context: MediaNavigationContext
    ) {

        connection.prepareStatement(
            """
        INSERT INTO media_navigation_contexts (
            context_id,
            asset_id,
            name,
            created_at,
            active_version_id
        )
        VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                context.contextId
            )

            statement.setString(
                2,
                context.assetId
            )

            statement.setString(
                3,
                context.name
            )

            statement.setLong(
                4,
                context.createdAt
            )

            statement.setString(
                5,
                context.activeVersion.versionId
            )

            statement.executeUpdate()
        }
    }

    private fun insertVersion(
        connection: Connection,
        version: MediaNavigationContextVersion
    ) {

        val rangesJson =
            json.encodeToString(
                ListSerializer(MediaRange.serializer()),
                version.mediaRanges
            )

        connection.prepareStatement(
            """
        INSERT INTO media_navigation_context_versions (
            version_id,
            context_id,
            version_number,
            created_at,
            ranges_json
        )
        VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                version.versionId
            )

            statement.setString(
                2,
                version.contextId
            )

            statement.setInt(
                3,
                version.versionNumber
            )

            statement.setLong(
                4,
                version.createdAt
            )

            statement.setString(
                5,
                rangesJson
            )

            statement.executeUpdate()
        }
    }

    private fun findVersionById(
        connection: Connection,
        versionId: String
    ): MediaNavigationContextVersion? {

        connection.prepareStatement(
            """
        SELECT
            version_id,
            context_id,
            version_number,
            created_at,
            ranges_json
        FROM media_navigation_context_versions
        WHERE version_id = ?
        """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                versionId
            )

            statement.executeQuery().use { resultSet ->

                if (!resultSet.next()) {
                    return null
                }

                val mediaRanges =
                    json.decodeFromString(
                        ListSerializer(MediaRange.serializer()),
                        resultSet.getString(
                            "ranges_json"
                        )
                    )

                return MediaNavigationContextVersion(
                    versionId =
                        resultSet.getString(
                            "version_id"
                        ),
                    contextId =
                        resultSet.getString(
                            "context_id"
                        ),
                    versionNumber =
                        resultSet.getInt(
                            "version_number"
                        ),
                    createdAt =
                        resultSet.getLong(
                            "created_at"
                        ),
                    mediaRanges =
                        mediaRanges
                )
            }
        }
    }

}
