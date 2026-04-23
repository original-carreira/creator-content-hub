package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.*
import com.creatorcontenthub.infrastructure.config.DatabaseConfig
import java.sql.DriverManager

class PostgresJobRepository : JobRepository {

    private val url = DatabaseConfig.URL
    private val user = DatabaseConfig.USER
    private val password = DatabaseConfig.PASSWORD

    override fun create(jobId: String) {
        val now = System.currentTimeMillis()

        DriverManager.getConnection(url, user, password).use { conn ->
            val stmt = conn.prepareStatement(
                """
                INSERT INTO jobs (job_id, status, created_at, started_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            )

            stmt.setString(1, jobId)
            stmt.setString(2, JobStatus.PROCESSING.name)
            stmt.setLong(3, now)
            stmt.setLong(4, now)

            stmt.executeUpdate()
        }
    }

    override fun markDone(
        jobId: String,
        transcription: String,
        summary: String,
        summaryCompletedAt: Long
    ) {
        val now = System.currentTimeMillis()

        val safeSummaryCompletedAt = minOf(summaryCompletedAt, now)

        DriverManager.getConnection(url, user, password).use { conn ->
            val stmt = conn.prepareStatement(
                """
            UPDATE jobs
            SET status = ?, 
                finished_at = ?, 
                transcription = ?, 
                transcription_completed_at = ?, 
                summary = ?, 
                summary_completed_at = ?,
                error_type = NULL,
                error_message = NULL
            WHERE job_id = ?
            """.trimIndent()
            )

            stmt.setString(1, JobStatus.DONE.name)
            stmt.setLong(2, now)
            stmt.setString(3, transcription)
            stmt.setLong(4, now)
            stmt.setString(5, summary)
            stmt.setLong(6, safeSummaryCompletedAt)
            stmt.setString(7, jobId)

            stmt.executeUpdate()
        }
    }

    override fun markFailed(
        jobId: String,
        errorType: ErrorType,
        errorMessage: String
    ) {
        val now = System.currentTimeMillis()

        DriverManager.getConnection(url, user, password).use { conn ->
            val stmt = conn.prepareStatement(
                """
                UPDATE jobs
                SET status = ?, 
                    finished_at = ?, 
                    error_type = ?, 
                    error_message = ?, 
                    transcription = NULL,
                    transcription_completed_at = NULL,
                    summary = NULL,
                    summary_completed_at = NULL
                WHERE job_id = ?
                """.trimIndent()
            )

            stmt.setString(1, JobStatus.FAILED.name)
            stmt.setLong(2, now)
            stmt.setString(3, errorType.name)
            stmt.setString(4, errorMessage)
            stmt.setString(5, jobId)

            stmt.executeUpdate()
        }
    }

    override fun findById(jobId: String): JobState? {
        DriverManager.getConnection(url, user, password).use { conn ->
            val stmt = conn.prepareStatement(
                "SELECT * FROM jobs WHERE job_id = ?"
            )

            stmt.setString(1, jobId)

            val rs = stmt.executeQuery()

            if (!rs.next()) return null

            return JobState(
                runCatching { JobStatus.valueOf(rs.getString("status")) }
                    .getOrElse { JobStatus.FAILED },
                createdAt = rs.getLong("created_at"),
                startedAt = rs.getLong("started_at"),
                finishedAt = rs.getLong("finished_at").takeIf { !rs.wasNull() },
                errorType = rs.getString("error_type")?.let {
                    runCatching { ErrorType.valueOf(it) }.getOrNull()},
                errorMessage = rs.getString("error_message"),
                transcription = rs.getString("transcription"),
                transcriptionCompletedAt = rs.getLong("transcription_completed_at")
                    .takeIf { !rs.wasNull() },
                summary = rs.getString("summary"),
                summaryCompletedAt = rs.getLong("summary_completed_at")
                    .takeIf { !rs.wasNull() }
            )
        }
    }

    override fun exists(jobId: String): Boolean {
        DriverManager.getConnection(url, user, password).use { conn ->
            val stmt = conn.prepareStatement(
                "SELECT 1 FROM jobs WHERE job_id = ?"
            )

            stmt.setString(1, jobId)

            val rs = stmt.executeQuery()
            return rs.next()
        }
    }

    override fun cleanup() {
        val ttlMillis = 10 * 60 * 1000L
        val now = System.currentTimeMillis()

        DriverManager.getConnection(url, user, password).use { conn ->
            val stmt = conn.prepareStatement(
                """
                DELETE FROM jobs
                WHERE COALESCE(finished_at, started_at) < ?
                """.trimIndent()
            )

            stmt.setLong(1, now - ttlMillis)
            stmt.executeUpdate()
        }
    }
}