package com.creatorcontenthub
import com.creatorcontenthub.controller.healthRoutes
import com.creatorcontenthub.controller.textRoutes

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
        )
    }

    // 2. Dentro de routing, você apenas chama a função
    routing {
        healthRoutes()
        textRoutes()
    }
}
