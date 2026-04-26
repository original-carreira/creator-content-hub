package com.creatorcontenthub.infrastructure.config

import com.creatorcontenthub.infrastructure.metrics.PrometheusRegistry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig

object DataSourceFactory {

    fun create(config: ApplicationConfig): HikariDataSource {

        val jdbcUrl = System.getenv("DB_URL")
            ?.takeIf { it.isNotBlank() }
            ?: config.property("database.url").getString()

        val username = System.getenv("DB_USER")
            ?.takeIf { it.isNotBlank() }
            ?: config.property("database.user").getString()

        val password = System.getenv("DB_PASSWORD")
            ?.takeIf { it.isNotBlank() }
            ?: config.property("database.password").getString()

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password

            poolName = "creator-content-hub-pool"

            maximumPoolSize = 10
            minimumIdle = 2 // 🔥 importante para estabilidade
            connectionTimeout = 5000
            idleTimeout = 600000
            maxLifetime = 1800000

            isAutoCommit = true

            metricRegistry = PrometheusRegistry.registry

            initializationFailTimeout = -1

            validate()
        }

        return HikariDataSource(hikariConfig)
    }
}