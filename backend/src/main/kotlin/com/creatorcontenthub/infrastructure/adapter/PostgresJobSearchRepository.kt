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

        val normalizedQuery = query
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")

        if (normalizedQuery.isBlank()) return emptyList()

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
                    OR job_id = ? -- 🔥 FALLBACK DIRETO
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

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->

                var i = 1

                stmt.setString(i++, normalizedQuery)
                stmt.setString(i++, normalizedQuery)

                stmt.setDouble(i++, recencyDecay)

                stmt.setString(i++, normalizedQuery) // ILIKE
                stmt.setString(i++, normalizedQuery) // job_id fallback

                stmt.setString(i++, status)
                stmt.setString(i++, status)

                stmt.setObject(i++, from)
                stmt.setObject(i++, from)

                stmt.setObject(i++, to)
                stmt.setObject(i++, to)

                stmt.setDouble(i++, rankWeight)
                stmt.setDouble(i++, timeWeight)

                stmt.setDouble(i++, doneBoost)
                stmt.setDouble(i++, failedBoost)
                stmt.setDouble(i++, defaultBoost)

                stmt.setDouble(i++, maxScore)

                stmt.setInt(i++, limit)
                stmt.setInt(i++, offset)

                val rs = stmt.executeQuery()
                val results = mutableListOf<JobSearchResult>()

                while (rs.next()) {
                    val createdAt = try {
                        Instant.ofEpochMilli(rs.getLong("created_at"))
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

                // 🔥 FALLBACK FINAL (CASO ZERO RESULTADOS)
                if (results.isEmpty()) {
                    val fallbackSql = """
                    SELECT job_id, status, created_at, summary
                    FROM jobs
                    WHERE job_id = ?
                    LIMIT ? OFFSET ?
                """.trimIndent()

                    conn.prepareStatement(fallbackSql).use { fallbackStmt ->
                        fallbackStmt.setString(1, normalizedQuery)
                        fallbackStmt.setInt(2, limit)
                        fallbackStmt.setInt(3, offset)

                        val fallbackRs = fallbackStmt.executeQuery()

                        while (fallbackRs.next()) {
                            val createdAt = Instant.ofEpochMilli(fallbackRs.getLong("created_at"))

                            results.add(
                                JobSearchResult(
                                    jobId = fallbackRs.getString("job_id"),
                                    status = fallbackRs.getString("status"),
                                    createdAt = createdAt.toString(),
                                    snippet = fallbackRs.getString("summary") ?: "",
                                    rank = 0.0,
                                    recencyScore = 0.0,
                                    finalScore = 0.0
                                )
                            )
                        }
                    }
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