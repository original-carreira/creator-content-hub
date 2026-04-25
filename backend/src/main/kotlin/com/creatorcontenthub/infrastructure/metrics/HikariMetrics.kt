package com.creatorcontenthub.infrastructure.metrics

import com.zaxxer.hikari.HikariDataSource

class HikariMetrics(
    private val dataSource: HikariDataSource
) {

    fun snapshot(): Triple<Int, Int, Int> {
        val pool = dataSource.hikariPoolMXBean

        return Triple(
            pool.activeConnections,
            pool.idleConnections,
            pool.threadsAwaitingConnection
        )
    }
}