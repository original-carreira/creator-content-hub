package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.infrastructure.config.DatabaseConfig
import java.sql.DriverManager

class PostgresJobRepository : JobRepository {

    private val url = DatabaseConfig.URL
    private val user = DatabaseConfig.USER
    private val password = DatabaseConfig.PASSWORD

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

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
                stmt.setString(2, job.status.name)
                stmt.setLong(3, job.createdAt)
                stmt.setLong(4, job.startedAt)
                stmt.setObject(5, job.finishedAt)

                stmt.setString(6, job.transcription)
                stmt.setObject(7, job.transcriptionCompletedAt)

                stmt.setString(8, job.summary)
                stmt.setObject(9, job.summaryCompletedAt)

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

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, job.status.name)
                stmt.setObject(2, job.finishedAt)

                stmt.setString(3, job.transcription)
                stmt.setObject(4, job.transcriptionCompletedAt)

                stmt.setString(5, job.summary)
                stmt.setObject(6, job.summaryCompletedAt)

                stmt.setString(7, job.errorType?.name)
                stmt.setString(8, job.errorMessage)

                stmt.setString(9, jobId)

                stmt.executeUpdate()
            }
        }
    }

    override fun findById(jobId: String): JobState? {
        val sql = "SELECT * FROM jobs WHERE job_id = ?"

        DriverManager.getConnection(url, user, password).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)

                val rs = stmt.executeQuery()

                if (!rs.next()) return null

                return JobState(
                    status = runCatching {
                        JobStatus.valueOf(rs.getString("status"))
                    }.getOrElse {
                        JobStatus.FAILED
                    },
                    createdAt = rs.getLong("created_at"),
                    startedAt = rs.getLong("started_at"),
                    finishedAt = (rs.getObject("finished_at") as? Number)?.toLong(),

                    transcription = rs.getString("transcription"),
                    transcriptionCompletedAt = (rs.getObject("transcription_completed_at") as? Number)?.toLong(),

                    summary = rs.getString("summary"),
                    summaryCompletedAt = (rs.getObject("summary_completed_at") as? Number)?.toLong(),

                    errorType = rs.getString("error_type")?.let {
                        runCatching { ErrorType.valueOf(it) }.getOrNull()
                    },
                    errorMessage = rs.getString("error_message")
                )
            }
        }
    }
}