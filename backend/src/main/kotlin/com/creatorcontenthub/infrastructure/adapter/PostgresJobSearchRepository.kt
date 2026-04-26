package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.JobSearchRepository
import com.creatorcontenthub.application.port.JobSearchResult
import javax.sql.DataSource
import java.time.Instant

class PostgresJobSearchRepository(
    private val dataSource: DataSource
) : JobSearchRepository {

    override fun search(query: String, limit: Int, offset: Int): List<JobSearchResult> {

        val sql = """
        SELECT job_id,
               status,
               created_at,
               COALESCE(SUBSTRING(summary FROM 1 FOR 200), '') AS snippet,
               COALESCE(ts_rank(search_vector, plainto_tsquery(?)), 0) AS rank
        FROM jobs
        WHERE search_vector @@ plainto_tsquery(?)
        AND created_at IS NOT NULL
        ORDER BY rank DESC
        LIMIT ? OFFSET ?
    """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, query)
                stmt.setString(2, query)
                stmt.setInt(3, limit)
                stmt.setInt(4, offset)

                val rs = stmt.executeQuery()
                val results = mutableListOf<JobSearchResult>()

                while (rs.next()) {

                    val createdAt = try {
                        val epoch = rs.getLong("created_at")

                        when {
                            epoch > 1_000_000_000_000 -> Instant.ofEpochMilli(epoch)
                            epoch > 0 -> Instant.EPOCH
                            else -> Instant.EPOCH
                        }
                    } catch (e: Exception) {
                        Instant.EPOCH
                    }

                    results.add(
                        JobSearchResult(
                            jobId = rs.getString("job_id"),
                            status = rs.getString("status"),
                            createdAt = createdAt.toString(), // ✅ CORRETO AGORA
                            snippet = rs.getString("snippet") ?: "",
                            rank = rs.getDouble("rank")
                        )
                    )
                }

                return results
            }
        }
    }

    override fun count(query: String): Long {
        val sql = """
            SELECT COUNT(*)
            FROM jobs
            WHERE search_vector @@ plainto_tsquery(?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, query)

                val rs = stmt.executeQuery()
                rs.next()

                return rs.getLong(1)
            }
        }
    }
}