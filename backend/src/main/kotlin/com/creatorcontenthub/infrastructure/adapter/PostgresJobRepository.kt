package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import java.sql.Types
import javax.sql.DataSource

class PostgresJobRepository(
    private val dataSource: DataSource
) : JobRepository {

    override fun create(jobId: String, job: JobState) {
        val sql = """
            INSERT INTO jobs (
                job_id,
                status,
                created_at,
                started_at,
                finished_at,
                transcription,
                transcription_completed_at,
                summary,
                summary_completed_at,
                error_type,
                error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
                stmt.setString(2, job.status.name)
                stmt.setLong(3, job.createdAt)
                stmt.setLong(4, job.startedAt)

                if (job.finishedAt != null)
                    stmt.setLong(5, job.finishedAt)
                else
                    stmt.setNull(5, Types.BIGINT)

                stmt.setString(6, job.transcription)

                if (job.transcriptionCompletedAt != null)
                    stmt.setLong(7, job.transcriptionCompletedAt)
                else
                    stmt.setNull(7, Types.BIGINT)

                stmt.setString(8, job.summary)

                if (job.summaryCompletedAt != null)
                    stmt.setLong(9, job.summaryCompletedAt)
                else
                    stmt.setNull(9, Types.BIGINT)

                stmt.setString(10, job.errorType?.name)
                stmt.setString(11, job.errorMessage)

                stmt.executeUpdate()
            }
        }
    }

    override fun update(jobId: String, job: JobState) {
        val sql = """
            UPDATE jobs SET
                status = ?,
                finished_at = ?,
                transcription = ?,
                transcription_completed_at = ?,
                summary = ?,
                summary_completed_at = ?,
                error_type = ?,
                error_message = ?
            WHERE job_id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, job.status.name)

                if (job.finishedAt != null)
                    stmt.setLong(2, job.finishedAt)
                else
                    stmt.setNull(2, Types.BIGINT)

                stmt.setString(3, job.transcription)

                if (job.transcriptionCompletedAt != null)
                    stmt.setLong(4, job.transcriptionCompletedAt)
                else
                    stmt.setNull(4, Types.BIGINT)

                stmt.setString(5, job.summary)

                if (job.summaryCompletedAt != null)
                    stmt.setLong(6, job.summaryCompletedAt)
                else
                    stmt.setNull(6, Types.BIGINT)

                stmt.setString(7, job.errorType?.name)
                stmt.setString(8, job.errorMessage)

                stmt.setString(9, jobId)

                stmt.executeUpdate()
            }
        }
    }

    override fun findById(jobId: String): JobState? {
        val sql = "SELECT * FROM jobs WHERE job_id = ?"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)

                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return null

                    return JobState(
                        status = runCatching {
                            JobStatus.valueOf(rs.getString("status"))
                        }.getOrElse {
                            JobStatus.FAILED
                        },
                        createdAt = rs.getLong("created_at"),
                        startedAt = rs.getLong("started_at"),
                        finishedAt = rs.getLongOrNull("finished_at"),

                        transcription = rs.getString("transcription"),
                        transcriptionCompletedAt = rs.getLongOrNull("transcription_completed_at"),

                        summary = rs.getString("summary"),
                        summaryCompletedAt = rs.getLongOrNull("summary_completed_at"),

                        errorType = rs.getString("error_type")?.let {
                            runCatching { ErrorType.valueOf(it) }.getOrNull()
                        },
                        errorMessage = rs.getString("error_message")
                    )
                }
            }
        }
    }

    // Helper seguro para BIGINT nullable
    private fun java.sql.ResultSet.getLongOrNull(column: String): Long? {
        val value = this.getLong(column)
        return if (this.wasNull()) null else value
    }
}