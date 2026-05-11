package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobQueueRepository
import com.creatorcontenthub.domain.model.JobQueueItem
import com.creatorcontenthub.domain.model.QueueStatus
import javax.sql.DataSource

class PostgresJobQueueRepository(
    private val dataSource: DataSource
) : JobQueueRepository {

    override fun enqueue(item: JobQueueItem) {

        val sql = """
            INSERT INTO job_queue (
                job_id,
                status,
                created_at,
                started_at,
                completed_at,
                attempts,
                error_message
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setString(1, item.jobId)
                stmt.setString(2, item.status.name)
                stmt.setLong(3, item.createdAt)

                stmt.setObject(4, item.startedAt)
                stmt.setObject(5, item.completedAt)

                stmt.setInt(6, item.attempts)
                stmt.setString(7, item.errorMessage)

                stmt.executeUpdate()
            }
        }
    }

    override fun claimNextPending(
        workerId: String,
        startedAt: Long
    ): JobQueueItem? {

        val sql = """
        UPDATE job_queue
        SET
            status = 'PROCESSING',
            started_at = ?,
            claimed_by = ?,
            last_heartbeat_at = ?,
            attempts = attempts + 1
        WHERE id = (
            SELECT id
            FROM job_queue
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        )
        RETURNING *
    """.trimIndent()

        dataSource.connection.use { conn ->

            conn.autoCommit = false

            try {

                conn.prepareStatement(sql).use { stmt ->

                    stmt.setLong(1, startedAt)
                    stmt.setString(2, workerId)
                    stmt.setLong(3, startedAt)

                    stmt.executeQuery().use { rs ->

                        if (!rs.next()) {
                            conn.commit()
                            return null
                        }

                        val item = JobQueueItem(
                            id = rs.getLong("id"),
                            jobId = rs.getString("job_id"),
                            status = QueueStatus.valueOf(
                                rs.getString("status")
                            ),
                            createdAt = rs.getLong("created_at"),
                            startedAt = rs.getLong("started_at")
                                .takeIf { !rs.wasNull() },
                            completedAt = rs.getLong("completed_at")
                                .takeIf { !rs.wasNull() },
                            attempts = rs.getInt("attempts"),
                            errorMessage = rs.getString("error_message")
                        )

                        conn.commit()

                        return item
                    }
                }

            } catch (ex: Exception) {

                conn.rollback()
                throw ex
            }
        }
    }

    override fun markCompleted(
        queueId: Long,
        completedAt: Long
    ) {

        val sql = """
        UPDATE job_queue
        SET status = 'COMPLETED',
            completed_at = ?
        WHERE id = ?
    """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setLong(1, completedAt)
                stmt.setLong(2, queueId)

                stmt.executeUpdate()
            }
        }
    }

    override fun markFailed(
        queueId: Long,
        completedAt: Long,
        errorMessage: String?
    ) {

        val sql = """
        UPDATE job_queue
        SET status = 'FAILED',
            completed_at = ?,
            error_message = ?
        WHERE id = ?
    """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setLong(1, completedAt)
                stmt.setString(2, errorMessage)
                stmt.setLong(3, queueId)

                stmt.executeUpdate()
            }
        }
    }
}