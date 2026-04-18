package com.creatorhub

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*

fun main() {
    // Alteração de "0.0.1" para "0.0.0.0" para aceitar conexões locais corretamente
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Configura o projeto para aceitar JSON
    install(ContentNegotiation) {
        json()
    }

    // Define as rotas (Antigo Módulo 1)
    routing {
        get("/health") {
            call.respond(mapOf("status" to "OK", "message" to "Creator Content Hub is running"))
        }
    }
}
