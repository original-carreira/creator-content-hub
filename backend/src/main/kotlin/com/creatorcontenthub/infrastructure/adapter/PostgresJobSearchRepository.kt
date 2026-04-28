package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobSearchRepository
import com.creatorcontenthub.application.port.JobSearchResult
import javax.sql.DataSource
import java.time.Instant

class PostgresJobSearchRepository(
    private val dataSource: DataSource,
    private val rankWeight: Double,
    private val timeWeight: Double,
    private val doneBoost: Double,
    private val failedBoost: Double,
    private val defaultBoost: Double,
    private val maxScore: Double,
    private val recencyDecay: Double
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
                SELECT
                    COALESCE(
                        websearch_to_tsquery('portuguese', ?),
                        plainto_tsquery('simple', ?)
                    ) AS q
            ),
            scored AS (
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

                    COALESCE(ts_rank_cd(search_vector, query.q) * 2.0, 0) AS rank,

                    EXP(
                        -(
                            EXTRACT(EPOCH FROM (NOW() - to_timestamp(created_at / 1000))) / 86400
                        ) / CAST(? AS DOUBLE PRECISION)
                    ) AS recency_score

                FROM jobs, query
                WHERE
                    (
                        search_vector @@ query.q
                        OR summary ILIKE '%' || ? || '%'
                    )
                    AND created_at IS NOT NULL
                    AND (?::text IS NULL OR status = ?)
                    AND (?::bigint IS NULL OR created_at >= ?)
                    AND (?::bigint IS NULL OR created_at <= ?)
            )
            SELECT
                job_id,
                status,
                created_at,
                snippet,
                rank,
                recency_score,

                GREATEST(
                    LEAST(
                        (
                            (
                                rank * ? +
                                recency_score * ?
                            ) *
                            CASE
                                WHEN status = 'DONE' THEN ?
                                WHEN status = 'FAILED' THEN ?
                                ELSE ?
                            END
                        ),
                        ?
                    ),
                    0
                ) AS final_score

            FROM scored
            ORDER BY final_score DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val sanitizedQuery = query
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
        if (sanitizedQuery.isBlank()) return emptyList()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                var i = 1

                stmt.setString(i++, sanitizedQuery) // websearch
                stmt.setString(i++, sanitizedQuery) // fallback simple

                stmt.setDouble(i++, recencyDecay)

                stmt.setString(i++, sanitizedQuery) // ILIKE fallback

                // status
                stmt.setString(i++, status)
                stmt.setString(i++, status)

                // from
                stmt.setObject(i++, from)
                stmt.setObject(i++, from)

                // to
                stmt.setObject(i++, to)
                stmt.setObject(i++, to)

                // weights
                stmt.setDouble(i++, rankWeight)
                stmt.setDouble(i++, timeWeight)

                // status boost
                stmt.setDouble(i++, doneBoost)
                stmt.setDouble(i++, failedBoost)
                stmt.setDouble(i++, defaultBoost)

                stmt.setDouble(i++, maxScore)

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
                            rank = rs.getDouble("rank"),
                            recencyScore = rs.getDouble("recency_score"),
                            finalScore = rs.getDouble("final_score")
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
                SELECT 
                    COALESCE(
                        websearch_to_tsquery('portuguese', ?),
                        plainto_tsquery('simple', ?)
                    ) AS q
            )
            SELECT COUNT(*)
            FROM jobs, query
            WHERE
                (
                    search_vector @@ query.q
                    OR summary ILIKE '%' || ? || '%'
                )
                AND created_at IS NOT NULL
                AND (?::text IS NULL OR status = ?)
                AND (?::bigint IS NULL OR created_at >= ?)
                AND (?::bigint IS NULL OR created_at <= ?)
        """.trimIndent()

        val sanitizedQuery = query
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
        if (sanitizedQuery.isBlank()) return 0

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                var i = 1

                stmt.setString(i++, sanitizedQuery) // websearch
                stmt.setString(i++, sanitizedQuery) // fallback simple
                stmt.setString(i++, sanitizedQuery) // ILIKE fallback

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