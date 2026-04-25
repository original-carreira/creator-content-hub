package com.creatorcontenthub.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import javax.sql.DataSource

fun Route.healthDbRoute(dataSource: DataSource) {

    get("/health/db") {

        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.queryTimeout = 2
                    stmt.execute("SELECT 1")
                }
            }

            call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))

        } catch (ex: Exception) {

            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "status" to "DOWN",
                    "error" to (ex.message ?: "unknown error")
                )
            )
        }
    }
}