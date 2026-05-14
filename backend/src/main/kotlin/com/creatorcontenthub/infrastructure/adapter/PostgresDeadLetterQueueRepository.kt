package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.DeadLetterQueueRepository
import com.creatorcontenthub.domain.model.DeadLetterQueueItem
import javax.sql.DataSource

class PostgresDeadLetterQueueRepository(
    private val dataSource: DataSource
) : DeadLetterQueueRepository {

    override fun insert(
        item: DeadLetterQueueItem
    ): Long {

        val sql = """
            INSERT INTO dead_letter_queue (
                job_id,
                queue_id,
                stage,
                error_message,
                failed_at,
                attempts,
                worker_id,
                payload_snapshot
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setString(1, item.jobId)

                stmt.setObject(2, item.queueId)

                stmt.setString(3, item.stage)
                stmt.setString(4, item.errorMessage)

                stmt.setLong(5, item.failedAt)

                stmt.setInt(6, item.attempts)

                stmt.setString(7, item.workerId)

                stmt.setString(8, item.payloadSnapshot)

                stmt.executeQuery().use { rs ->

                    rs.next()

                    return rs.getLong("id")
                }
            }
        }
    }

    override fun findById(
        id: Long
    ): DeadLetterQueueItem? {

        val sql = """
            SELECT *
            FROM dead_letter_queue
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setLong(1, id)

                stmt.executeQuery().use { rs ->

                    if (!rs.next()) {
                        return null
                    }

                    return mapRow(rs)
                }
            }
        }
    }

    override fun list(
        limit: Int,
        offset: Int
    ): List<DeadLetterQueueItem> {

        val sql = """
            SELECT *
            FROM dead_letter_queue
            ORDER BY failed_at DESC
            LIMIT ?
            OFFSET ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setInt(1, limit)
                stmt.setInt(2, offset)

                stmt.executeQuery().use { rs ->

                    val items = mutableListOf<DeadLetterQueueItem>()

                    while (rs.next()) {
                        items.add(mapRow(rs))
                    }

                    return items
                }
            }
        }
    }

    override fun delete(
        id: Long
    ): Boolean {

        val sql = """
            DELETE FROM dead_letter_queue
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setLong(1, id)

                return stmt.executeUpdate() > 0
            }
        }
    }

    private fun mapRow(
        rs: java.sql.ResultSet
    ): DeadLetterQueueItem {

        return DeadLetterQueueItem(
            id = rs.getLong("id"),

            jobId = rs.getString("job_id"),

            queueId = rs.getLong("queue_id")
                .takeIf { !rs.wasNull() },

            stage = rs.getString("stage"),

            errorMessage = rs.getString("error_message"),

            failedAt = rs.getLong("failed_at"),

            attempts = rs.getInt("attempts"),

            workerId = rs.getString("worker_id"),

            payloadSnapshot = rs.getString("payload_snapshot")
        )
    }
}