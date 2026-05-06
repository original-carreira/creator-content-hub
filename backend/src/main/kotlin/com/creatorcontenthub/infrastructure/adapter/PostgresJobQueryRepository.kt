package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobQueryRepository
import com.creatorcontenthub.application.query.JobListItemView
import com.creatorcontenthub.domain.model.JobStatus
import java.sql.ResultSet
import javax.sql.DataSource

class PostgresJobQueryRepository(
    private val dataSource: DataSource
) : JobQueryRepository {

    override fun findAll(
        status: JobStatus?,
        from: Long?,
        to: Long?,
        sort: String?,
        order: String?,
        limit: Int,
        offset: Int
    ): List<JobListItemView> {

        val sortColumn = when (sort) {
            "createdAt" -> "created_at"
            "status" -> "status"
            else -> "created_at"
        }

        val sortOrder = when (order?.lowercase()) {
            "asc" -> "ASC"
            else -> "DESC"
        }

        val sql = """
            SELECT 
                job_id,
                status,
                created_at,
                finished_at,
                transcription,
                summary,
                title,
                thumbnail_url
            FROM jobs
            WHERE 
                (? IS NULL OR status = ?)
                AND (? IS NULL OR created_at >= ?)
                AND (? IS NULL OR created_at <= ?)
            ORDER BY $sortColumn $sortOrder
            LIMIT ? OFFSET ?
        """.trimIndent()

        val results = mutableListOf<JobListItemView>()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                // status
                if (status != null) {
                    stmt.setString(1, status.name)
                    stmt.setString(2, status.name)
                } else {
                    stmt.setNull(1, java.sql.Types.VARCHAR)
                    stmt.setNull(2, java.sql.Types.VARCHAR)
                }

                // from
                if (from != null) {
                    stmt.setLong(3, from)
                    stmt.setLong(4, from)
                } else {
                    stmt.setNull(3, java.sql.Types.BIGINT)
                    stmt.setNull(4, java.sql.Types.BIGINT)
                }

                // to
                if (to != null) {
                    stmt.setLong(5, to)
                    stmt.setLong(6, to)
                } else {
                    stmt.setNull(5, java.sql.Types.BIGINT)
                    stmt.setNull(6, java.sql.Types.BIGINT)
                }

                stmt.setInt(7, limit)
                stmt.setInt(8, offset)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(mapRow(rs))
                    }
                }
            }
        }

        return results
    }

    override fun count(
        status: JobStatus?,
        from: Long?,
        to: Long?
    ): Long {

        val sql = """
        SELECT COUNT(*) 
        FROM jobs
        WHERE 
            (? IS NULL OR status = ?)
            AND (? IS NULL OR created_at >= ?)
            AND (? IS NULL OR created_at <= ?)
    """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                if (status != null) {
                    stmt.setString(1, status.name)
                    stmt.setString(2, status.name)
                } else {
                    stmt.setNull(1, java.sql.Types.VARCHAR)
                    stmt.setNull(2, java.sql.Types.VARCHAR)
                }

                if (from != null) {
                    stmt.setLong(3, from)
                    stmt.setLong(4, from)
                } else {
                    stmt.setNull(3, java.sql.Types.BIGINT)
                    stmt.setNull(4, java.sql.Types.BIGINT)
                }

                if (to != null) {
                    stmt.setLong(5, to)
                    stmt.setLong(6, to)
                } else {
                    stmt.setNull(5, java.sql.Types.BIGINT)
                    stmt.setNull(6, java.sql.Types.BIGINT)
                }

                stmt.executeQuery().use { rs ->
                    rs.next()
                    return rs.getLong(1)
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): JobListItemView {
        return JobListItemView(
            jobId = rs.getString("job_id"),
            status = runCatching {
                JobStatus.valueOf(rs.getString("status"))
            }.getOrElse {
                JobStatus.FAILED
            },
            createdAt = rs.getLong("created_at"),
            finishedAt = rs.getLongOrNull("finished_at"),
            hasTranscription = !rs.getString("transcription").isNullOrBlank(),
            hasSummary = !rs.getString("summary").isNullOrBlank(),

            title = rs.getString("title"),
            thumbnailUrl = rs.getString("thumbnail_url"),
            summary = rs.getString("summary")
        )
    }

    private fun ResultSet.getLongOrNull(column: String): Long? {
        val value = this.getLong(column)
        return if (this.wasNull()) null else value
    }
}