package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.ErrorType
import com.creatorcontenthub.domain.model.JobStage
import com.creatorcontenthub.domain.model.JobState
import com.creatorcontenthub.domain.model.JobStatus
import com.creatorcontenthub.domain.model.Transcript
import org.slf4j.LoggerFactory
import java.sql.Types
import javax.sql.DataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class PostgresJobRepository(
    private val dataSource: DataSource
) : JobRepository {
    private val logger = LoggerFactory.getLogger(PostgresJobRepository::class.java)

    private val json = Json

    override fun create(jobId: String, job: JobState) {

        // WRITE GUARD
        if (job.status.isFinal() && job.finishedAt == null) {
            logger.warn("event=write_guard_violation jobId={} status={}", jobId, job.status)
            throw IllegalStateException("Invalid state: final status without finishedAt")
        }

        val transcriptJson =
            job.transcript?.let {
                json.encodeToString(it)
            }

        val sql = """
            INSERT INTO jobs (
                job_id,
                status,
                stage,
                created_at,
                started_at,
                finished_at,
                video_id,
                title,
                transcription,
                transcript_json,
                transcription_completed_at,
                summary,
                summary_completed_at,
                transcription_path,
                summary_path,
                audio_path,
                video_path,
                thumbnail_url,
                error_type,
                error_message
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
                stmt.setString(2, job.status.name)
                stmt.setString(3, job.stage.name)
                stmt.setLong(4, job.createdAt)
                stmt.setLong(5, job.startedAt)

                stmt.setLongOrNull(6, job.finishedAt)
                stmt.setString(7, job.videoId)
                stmt.setString(8, job.title)

                stmt.setString(9, job.transcription)
                stmt.setString(10, transcriptJson)

                stmt.setLongOrNull(11, job.transcriptionCompletedAt)
                stmt.setString(12, job.summary)
                stmt.setLongOrNull(13, job.summaryCompletedAt)

                stmt.setString(14, job.transcriptionPath)
                stmt.setString(15, job.summaryPath)
                stmt.setString(16, job.audioPath)

                stmt.setString(17, job.videoPath)
                stmt.setString(18, job.thumbnailUrl)

                stmt.setString(19, job.errorType?.name)
                stmt.setString(20, job.errorMessage)

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

        val transcriptJson =
            job.transcript?.let {
                json.encodeToString(it)
            }

        val sql = """
            UPDATE jobs SET
                status = ?,
                stage = ?,
                video_id = ?,
                finished_at = ?,
                transcription = ?,
                transcript_json = ?,
                transcription_completed_at = ?,
                summary = ?,
                summary_completed_at = ?,
                error_type = ?,
                error_message = ?,
                title = ?,
                thumbnail_url = ?,
                transcription_path = ?,
                summary_path = ?,
                audio_path = ?,
                video_path = ?
            WHERE job_id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, job.status.name)
                stmt.setString(2, job.stage.name)
                stmt.setString(3, job.videoId)
                stmt.setLongOrNull(4, job.finishedAt)
                stmt.setString(5, job.transcription)
                stmt.setString(6, transcriptJson)
                stmt.setLongOrNull(7, job.transcriptionCompletedAt)
                stmt.setString(8, job.summary)
                stmt.setLongOrNull(9, job.summaryCompletedAt)
                stmt.setString(10, job.errorType?.name)
                stmt.setString(11, job.errorMessage)
                stmt.setString(12, job.title)

                stmt.setString(13, job.thumbnailUrl)

                stmt.setString(14, job.transcriptionPath)
                stmt.setString(15, job.summaryPath)
                stmt.setString(16, job.audioPath)
                stmt.setString(17, job.videoPath)

                stmt.setString(18, jobId)

                stmt.executeUpdate()
            }
        }
    }

    override fun delete(jobId: String) {
        val sql = "DELETE FROM jobs WHERE job_id = ?"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jobId)
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

                    val transcriptJson = rs.getString("transcript_json")

                    val transcript =
                        transcriptJson
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                json.decodeFromString<Transcript>(it)
                            }

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
                        stage = runCatching {
                            JobStage.valueOf(rs.getString("stage"))
                        }.getOrElse {
                            JobStage.UNKNOWN
                        },
                        createdAt = createdAt,
                        startedAt = startedAt,
                        videoId = rs.getString("video_id"),
                        finishedAt = safeFinishedAt,
                        transcription = safeTranscription,
                        transcript = transcript,
                        transcriptionCompletedAt = safeTranscriptionCompletedAt,
                        summary = summaryDb,
                        summaryCompletedAt = safeSummaryCompletedAt,
                        errorType = safeErrorType,
                        errorMessage = errorMessage,
                        title = rs.getString("title"),
                        thumbnailUrl = rs.getString("thumbnail_url")?.takeIf { it.isNotBlank() },
                        transcriptionPath = rs.getString("transcription_path")?.takeIf { it.isNotBlank() },
                        summaryPath = rs.getString("summary_path")?.takeIf { it.isNotBlank() },
                        audioPath = rs.getString("audio_path")?.takeIf { it.isNotBlank() },
                        videoPath = rs.getString("video_path")?.takeIf { it.isNotBlank() }
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

    override fun findByVideoId(videoId: String): JobState? {
        val sql = "SELECT * FROM jobs WHERE video_id = ? LIMIT 1"

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, videoId)

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

                    var safeFinishedAt = finishedAtDb

                    if (status.isFinal() && safeFinishedAt == null) {
                        logger.warn("event=invalid_persisted_state videoId={} status={}", videoId, status)
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

                    val transcriptJson = rs.getString("transcript_json")
                    val transcript =
                        transcriptJson
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                json.decodeFromString<Transcript>(it)
                            }

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
                        stage = runCatching {
                            JobStage.valueOf(rs.getString("stage"))
                        }.getOrElse {
                            JobStage.UNKNOWN
                        },
                        createdAt = createdAt,
                        startedAt = startedAt,
                        videoId = rs.getString("video_id"),
                        finishedAt = safeFinishedAt,
                        transcription = safeTranscription,
                        transcript = transcript,
                        transcriptionCompletedAt = safeTranscriptionCompletedAt,
                        summary = summaryDb,
                        summaryCompletedAt = safeSummaryCompletedAt,
                        errorType = safeErrorType,
                        errorMessage = errorMessage,
                        title = rs.getString("title"),
                        thumbnailUrl = rs.getString("thumbnail_url")?.takeIf { it.isNotBlank() },
                        transcriptionPath = rs.getString("transcription_path")?.takeIf { it.isNotBlank() },
                        summaryPath = rs.getString("summary_path")?.takeIf { it.isNotBlank() },
                        audioPath = rs.getString("audio_path")?.takeIf { it.isNotBlank() },
                        videoPath = rs.getString("video_path")?.takeIf { it.isNotBlank() }
                    )
                }
            }
        }
    }

    override fun findWithIdByVideoId(videoId: String): Pair<String, JobState>? {
        val sql = """
        SELECT job_id, status, created_at, started_at, finished_at,
               error_type, error_message,
               transcription, transcript_json, transcription_completed_at,
               summary, summary_completed_at,
               video_id, title,
               transcription_path, summary_path, audio_path,
               video_path,
               thumbnail_url
        FROM jobs
        WHERE video_id = ?
        ORDER BY created_at DESC
        LIMIT 1
    """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, videoId)

                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val jobId = rs.getString("job_id")

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

                    var safeFinishedAt = finishedAtDb

                    if (status.isFinal() && safeFinishedAt == null) {
                        logger.warn("event=invalid_persisted_state videoId={} status={}", videoId, status)
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

                    val transcriptJson = rs.getString("transcript_json")
                    val transcript =
                        transcriptJson
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                json.decodeFromString<Transcript>(it)
                            }

                    val rawErrorType = rs.getString("error_type")
                    val safeErrorType =
                        if (status == JobStatus.FAILED) {
                            rawErrorType?.let {
                                runCatching { ErrorType.valueOf(it) }.getOrNull()
                            } ?: ErrorType.UNKNOWN
                        } else null

                    val errorMessage = rs.getString("error_message")

                    val jobState = JobState(
                        status = status,
                        stage = runCatching {
                            JobStage.valueOf(rs.getString("stage"))
                        }.getOrElse {
                            JobStage.UNKNOWN
                        },
                        createdAt = createdAt,
                        startedAt = startedAt,
                        videoId = rs.getString("video_id"),
                        finishedAt = safeFinishedAt,
                        transcription = safeTranscription,
                        transcript = transcript,
                        transcriptionCompletedAt = safeTranscriptionCompletedAt,
                        summary = summaryDb,
                        summaryCompletedAt = safeSummaryCompletedAt,
                        errorType = safeErrorType,
                        errorMessage = errorMessage,
                        title = rs.getString("title"),
                        thumbnailUrl = rs.getString("thumbnail_url")?.takeIf { it.isNotBlank() },
                        transcriptionPath = rs.getString("transcription_path")?.takeIf { it.isNotBlank() },
                        summaryPath = rs.getString("summary_path")?.takeIf { it.isNotBlank() },
                        audioPath = rs.getString("audio_path")?.takeIf { it.isNotBlank() },
                        videoPath = rs.getString("video_path")?.takeIf { it.isNotBlank() }
                    )

                    return jobId to jobState
                }
            }
        }

        return null
    }
}
