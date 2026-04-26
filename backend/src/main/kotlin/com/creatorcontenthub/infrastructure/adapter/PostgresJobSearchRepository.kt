package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobSearchRepository
import com.creatorcontenthub.application.port.JobSearchResult
import javax.sql.DataSource
import java.time.Instant

class PostgresJobSearchRepository(
    private val dataSource: DataSource
) : JobSearchRepository {

    override fun search(
        query: String,
        status: String?,
        from: Long?,
        to: Long?,
        limit: Int,
        offset: Int
    ): List<JobSearchResult> {

        val sql = """
            WITH query AS (
                SELECT plainto_tsquery('portuguese', ?) AS q
            )
            SELECT
                job_id,
                status,
                created_at,
                COALESCE(
                    ts_headline(
                        'portuguese',
                        coalesce(summary, ''),
                        query.q
                    ),
                    ''
                ) AS snippet,
                COALESCE(ts_rank_cd(search_vector, query.q), 0) AS rank
        FROM jobs, query
        WHERE
            search_vector @@ query.q
            AND created_at IS NOT NULL
            AND (?::text IS NULL OR status = ?)
            AND (?::bigint IS NULL OR created_at >= ?)
            AND (?::bigint IS NULL OR created_at <= ?)
        ORDER BY rank DESC
        LIMIT ? OFFSET ?
    """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                var i = 1

                stmt.setString(i++, query)

                // status
                stmt.setString(i++, status)
                stmt.setString(i++, status)

                // from
                stmt.setObject(i++, from)
                stmt.setObject(i++, from)

                // to
                stmt.setObject(i++, to)
                stmt.setObject(i++, to)

                // pagination
                stmt.setInt(i++, limit)
                stmt.setInt(i++, offset)

                val rs = stmt.executeQuery()
                val results = mutableListOf<JobSearchResult>()

                while (rs.next()) {

                    val createdAt = try {
                        val epoch = rs.getLong("created_at")
                        Instant.ofEpochMilli(epoch)
                    } catch (e: Exception) {
                        Instant.EPOCH
                    }

                    results.add(
                        JobSearchResult(
                            jobId = rs.getString("job_id"),
                            status = rs.getString("status"),
                            createdAt = createdAt.toString(),
                            snippet = rs.getString("snippet") ?: "",
                            rank = rs.getDouble("rank")
                        )
                    )
                }

                return results
            }
        }
    }

    override fun count(
        query: String,
        status: String?,
        from: Long?,
        to: Long?
    ): Long {
        val sql = """
            WITH query AS (
                SELECT plainto_tsquery('portuguese', ?) AS q
            )
            SELECT COUNT(*)
            FROM jobs, query
            WHERE
                search_vector @@ query.q
                AND created_at IS NOT NULL
                AND (?::text IS NULL OR status = ?)
                AND (?::bigint IS NULL OR created_at >= ?)
                AND (?::bigint IS NULL OR created_at <= ?)
       """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                var i = 1

                stmt.setString(i++, query)

                // status
                stmt.setString(i++, status)
                stmt.setString(i++, status)

                // from
                stmt.setObject(i++, from)
                stmt.setObject(i++, from)

                // to
                stmt.setObject(i++, to)
                stmt.setObject(i++, to)

                val rs = stmt.executeQuery()
                rs.next()

                return rs.getLong(1)
            }
        }
    }
}