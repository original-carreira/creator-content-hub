package com.creatorcontenthub

import com.creatorcontenthub.controller.healthRoutes
import com.creatorcontenthub.controller.textRoutes
import com.creatorcontenthub.infrastructure.http.configureRequestId
import com.creatorcontenthub.infrastructure.http.configureStatusPages
import com.creatorcontenthub.infrastructure.http.requestId
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import io.ktor.server.request.* // Este traz o .path() e o .httpMethod


fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureRequestId()    // 1. gera o requestId no início do pipeline
    configureLogging()      // 2. logs já conseguem ler o requestId
    configureSerialization()
    configureStatusPages()  // 3. erros já terão requestId
    configureRouting()
}

// 🔧 LOGGING
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO

        // Só loga requisições de processamento para não sujar o log com /health
        filter { call ->
            call.request.path().startsWith("/process")
        }

        format { call ->
            val requestId = call.requestId()
            val method = call.request.httpMethod.value // Agora com o import correto
            val path = call.request.path()
            val status = call.response.status()?.value?.toString() ?: "Unknown"

            "HTTP $method $path -> $status"
        }
    }
}

// 🔧 SERIALIZAÇÃO (CRÍTICO)
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true // Recomendado para robustez no port do Python
            }
        )
    }
}

// 🌐 ROUTING
fun Application.configureRouting() {
    routing {
        healthRoutes()
        textRoutes()
    }
}
