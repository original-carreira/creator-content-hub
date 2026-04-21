package com.creatorcontenthub

import com.creatorcontenthub.controller.healthRoutes
import com.creatorcontenthub.controller.textRoutes
import com.creatorcontenthub.controller.exportRoutes
import com.creatorcontenthub.controller.ingestRoutes
import com.creatorcontenthub.controller.metricsRoutes
import com.creatorcontenthub.infrastructure.http.configureMetrics
import com.creatorcontenthub.infrastructure.http.configureRequestId
import com.creatorcontenthub.infrastructure.http.configureStatusPages
import com.creatorcontenthub.infrastructure.http.requestId
import com.creatorcontenthub.infrastructure.http.duration
import com.creatorcontenthub.application.usecase.ProcessTextUseCase
import com.creatorcontenthub.application.usecase.ExportTextUseCase
import com.creatorcontenthub.application.usecase.IngestYoutubeUseCase
import com.creatorcontenthub.infrastructure.adapter.LocalTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.PythonTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.FallbackTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.TxtExporterAdapter
import com.creatorcontenthub.infrastructure.adapter.YtDlpVideoIngestionAdapter
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.IngestionWindowMetrics
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import org.slf4j.LoggerFactory
import io.ktor.server.request.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("JobCleanupScheduler")

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureRequestId()
    configureLogging()
    configureSerialization()
    configureStatusPages()

    // =============================
    // PROCESSAMENTO
    // =============================

    val usePython = environment.config
        .propertyOrNull("app.usePython")
        ?.getString()
        ?.toBoolean() ?: false

    val endpoint = environment.config
        .propertyOrNull("app.python.endpoint")
        ?.getString()
        ?: "http://localhost:5000/process"

    val localAdapter = LocalTextProcessorAdapter()

    val adapter =
        if (usePython) {
            val pythonAdapter = PythonTextProcessorAdapter(endpoint)

            FallbackTextProcessorAdapter(
                primary = pythonAdapter,
                fallback = localAdapter
            )
        } else {
            localAdapter
        }

    val processTextUseCase = ProcessTextUseCase(adapter)

    // =============================
    // EXPORT
    // =============================

    val txtExporter = TxtExporterAdapter()
    val exportTextUseCase = ExportTextUseCase(txtExporter)

    // =============================
    // INGEST YOUTUBE + MÉTRICAS
    // =============================

    val executor = Executors.newFixedThreadPool(4)
    val jobStatusStore = InMemoryJobStatusStore()

    // 🔥 MÉTRICAS (SINGLETONS)
    val ingestionMetrics = IngestionMetrics()
    val ingestionWindowMetrics = IngestionWindowMetrics()

    val videoIngestionAdapter = YtDlpVideoIngestionAdapter(
        executor,
        jobStatusStore,
        ingestionMetrics,
        ingestionWindowMetrics
    )

    val ingestYoutubeUseCase = IngestYoutubeUseCase(
        videoIngestionAdapter,
        jobStatusStore,
        ingestionMetrics
    )

    // =============================
    // SCHEDULER
    // =============================

    val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "job-cleanup-scheduler").apply {
                isDaemon = true
            }
        }

    scheduler.scheduleAtFixedRate(
        {
            try {
                jobStatusStore.cleanup()
            } catch (ex: Exception) {
                log.error("Error during job cleanup", ex)
            }
        },
        1,
        1,
        TimeUnit.MINUTES
    )

    environment.monitor.subscribe(ApplicationStopped) {
        log.info("Shutting down job cleanup scheduler...")

        scheduler.shutdown()

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (ex: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    // =============================
    // ROUTING
    // =============================

    configureRouting(
        processTextUseCase,
        exportTextUseCase,
        ingestYoutubeUseCase,
        jobStatusStore,
        ingestionMetrics,
        ingestionWindowMetrics // 🔥 PASSANDO CORRETAMENTE
    )
}

// 🔧 LOGGING
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            call.request.path().startsWith("/process")
        }

        format { call ->
            val requestId = call.requestId()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val status = call.response.status()?.value?.toString() ?: "Unknown"
            val duration = call.duration()

            "[requestId=$requestId] HTTP $method $path -> $status (${duration}ms)"
        }
    }
}

// 🔧 SERIALIZAÇÃO
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            }
        )
    }
}

// 🌐 ROUTING
fun Application.configureRouting(
    processTextUseCase: ProcessTextUseCase,
    exportTextUseCase: ExportTextUseCase,
    ingestYoutubeUseCase: IngestYoutubeUseCase,
    jobStatusStore: InMemoryJobStatusStore,
    ingestionMetrics: IngestionMetrics,
    ingestionWindowMetrics: IngestionWindowMetrics // 🔥 ADICIONADO
) {
    routing {
        healthRoutes()
        textRoutes(processTextUseCase)
        exportRoutes(exportTextUseCase)

        ingestRoutes(
            ingestYoutubeUseCase,
            jobStatusStore
        )

        // 🔥 MÉTRICAS COMPLETAS
        metricsRoutes(
            ingestionMetrics,
            ingestionWindowMetrics
        )
    }
}