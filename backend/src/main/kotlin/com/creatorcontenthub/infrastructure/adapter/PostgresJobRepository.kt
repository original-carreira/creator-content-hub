package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import org.slf4j.LoggerFactory
import java.sql.Types
import javax.sql.DataSource

class PostgresJobRepository(
    private val dataSource: DataSource
) : JobRepository {
    private val logger = LoggerFactory.getLogger(PostgresJobRepository::class.java)

    override fun create(jobId: String, job: JobState) {

        // WRITE GUARD
        if (job.status.isFinal() && job.finishedAt == null) {
            logger.warn("event=write_guard_violation jobId={} status={}", jobId, job.status)
            throw IllegalStateException("Invalid state: final status without finishedAt")
        }

        val sql = """
            INSERT INTO jobs (
                job_id, status, created_at, started_at, finished_at,
                transcription, transcription_completed_at, summary, summary_completed_at,
                error_type, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
                stmt.setString(2, job.status.name)
                stmt.setLong(3, job.createdAt)
                stmt.setLong(4, job.startedAt)

                stmt.setLongOrNull(5, job.finishedAt)
                stmt.setString(6, job.transcription)
                stmt.setLongOrNull(7, job.transcriptionCompletedAt)
                stmt.setString(8, job.summary)
                stmt.setLongOrNull(9, job.summaryCompletedAt)
                stmt.setString(10, job.errorType?.name)
                stmt.setString(11, job.errorMessage)

                stmt.executeUpdate()
            }
        }
    }

    override fun update(jobId: String, job: JobState) {

        // WRITE GUARD
        if (job.status.isFinal() && job.finishedAt == null) {
            logger.warn("event=write_guard_violation jobId={} status={}", jobId, job.status)
            throw IllegalStateException("Invalid state: final status without finishedAt")
        }

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
                stmt.setLongOrNull(2, job.finishedAt)
                stmt.setString(3, job.transcription)
                stmt.setLongOrNull(4, job.transcriptionCompletedAt)
                stmt.setString(5, job.summary)
                stmt.setLongOrNull(6, job.summaryCompletedAt)
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

                    val createdAt = rs.getLong("created_at")
                    val startedAt = rs.getLong("started_at")

                    val finishedAtDb = (rs.getObject("finished_at") as? Number)?.toLong()
                    val transcriptionCompletedAtDb = (rs.getObject("transcription_completed_at") as? Number)?.toLong()
                    val summaryCompletedAtDb = (rs.getObject("summary_completed_at") as? Number)?.toLong()

                    val status = runCatching {
                        JobStatus.valueOf(rs.getString("status"))
                    }.getOrElse {
                        JobStatus.FAILED
                    }

                    // Regra de Correção Original mantida
                    var safeFinishedAt = finishedAtDb

                    if (status.isFinal() && safeFinishedAt == null) {
                        logger.warn("event=invalid_persisted_state jobId={} status={}", jobId, status)

                        // fallback seguro (legado)
                        safeFinishedAt = createdAt
                    }

                    val transcriptionDb = rs.getString("transcription")
                    val safeTranscription =
                        if (status == JobStatus.DONE && transcriptionDb.isNullOrBlank())
                            "[transcription missing]"
                        else
                            transcriptionDb

                    val safeTranscriptionCompletedAt =
                        if (!safeTranscription.isNullOrBlank())
                            transcriptionCompletedAtDb ?: createdAt
                        else
                            null

                    val summaryDb = rs.getString("summary")
                    val safeSummaryCompletedAt =
                        if (!summaryDb.isNullOrBlank())
                            summaryCompletedAtDb ?: createdAt
                        else
                            null

                    val rawErrorType = rs.getString("error_type")
                    val safeErrorType =
                        if (status == JobStatus.FAILED) {
                            rawErrorType?.let {
                                runCatching { ErrorType.valueOf(it) }.getOrNull()
                            } ?: ErrorType.UNKNOWN
                        } else null

                    val errorMessage = rs.getString("error_message")

                    return JobState(
                        status = status,
                        createdAt = createdAt,
                        startedAt = startedAt,
                        finishedAt = safeFinishedAt,
                        transcription = safeTranscription,
                        transcriptionCompletedAt = safeTranscriptionCompletedAt,
                        summary = summaryDb,
                        summaryCompletedAt = safeSummaryCompletedAt,
                        errorType = safeErrorType,
                        errorMessage = errorMessage
                    )
                }
            }
        }
    }

    @Deprecated("Use update(job) com JobState consistente")
    override fun updateStatus(jobId: String, status: JobStatus) {

        // WRITE GUARD (versão adaptada)
        if (status.isFinal()) {
            // Aqui não temos job.finishedAt explícito, mas garantimos que será setado
            // então não bloqueamos, apenas seguimos
        }

        val sql = """
        UPDATE jobs 
        SET status = ?, finished_at = ?
        WHERE job_id = ?
    """

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                stmt.setString(1, status.name)

                if (status.isFinal()) {
                    stmt.setLong(2, System.currentTimeMillis())
                } else {
                    stmt.setNull(2, java.sql.Types.BIGINT)
                }

                stmt.setString(3, jobId)

                stmt.executeUpdate()
            }
        }
    }

    override fun isCanceled(jobId: String): Boolean {
        val sql = "SELECT status FROM jobs WHERE job_id = ?"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)

                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return false
                    return rs.getString("status") == "CANCELED"
                }
            }
        }
    }

    override fun markCanceledIfNotFinal(jobId: String): Boolean {

        val sql = """
        UPDATE jobs
        SET status = ?, finished_at = ?
        WHERE job_id = ?
        AND status NOT IN ('DONE','FAILED','CANCELED')
    """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, JobStatus.CANCELED.name)
                stmt.setLong(2, System.currentTimeMillis())
                stmt.setString(3, jobId)

                val rows = stmt.executeUpdate()
                return rows > 0
            }
        }
    }

    private fun java.sql.PreparedStatement.setLongOrNull(index: Int, value: Long?) {
        if (value != null) this.setLong(index, value)
        else this.setNull(index, Types.BIGINT)
    }
}
