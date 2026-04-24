package com.creatorcontenthub.infrastructure.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import javax.sql.DataSource

object DataSourceFactory {

    fun create(config: ApplicationConfig): DataSource {

        // PRIORIDADE:
        // 1. ENV (produção)
        // 2. application.conf (dev)
        // 3. FAIL FAST

        val jdbcUrl = System.getenv("DB_URL")
            ?: config.propertyOrNull("database.url")?.getString()
            ?: error("Database URL not configured")

        val username = System.getenv("DB_USER")
            ?: config.propertyOrNull("database.user")?.getString()
            ?: error("Database user not configured")

        val password = System.getenv("DB_PASSWORD")
            ?: config.propertyOrNull("database.password")?.getString()
            ?: error("Database password not configured")

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password

            maximumPoolSize = 10
            connectionTimeout = 5000
            isAutoCommit = true

            validate()
        }

        return HikariDataSource(hikariConfig)
    }
}