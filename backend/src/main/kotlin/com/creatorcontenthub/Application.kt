package com.creatorcontenthub

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.controller.healthRoutes
import com.creatorcontenthub.controller.textRoutes
import com.creatorcontenthub.controller.exportRoutes
import com.creatorcontenthub.controller.ingestRoutes
import com.creatorcontenthub.controller.metricsRoutes
import com.creatorcontenthub.infrastructure.http.configureRequestId
import com.creatorcontenthub.infrastructure.http.configureStatusPages
import com.creatorcontenthub.infrastructure.http.requestId
import com.creatorcontenthub.infrastructure.http.duration
import com.creatorcontenthub.application.usecase.ProcessTextUseCase
import com.creatorcontenthub.application.usecase.ExportTextUseCase
import com.creatorcontenthub.application.usecase.IngestYoutubeUseCase
import com.creatorcontenthub.infrastructure.adapter.YtDlpVideoIngestionAdapter
import com.creatorcontenthub.infrastructure.adapter.WhisperTranscriptionAdapter
import com.creatorcontenthub.infrastructure.adapter.FallbackSummarizationAdapter
import com.creatorcontenthub.infrastructure.adapter.FallbackTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.LocalTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.PostgresJobRepository
import com.creatorcontenthub.infrastructure.adapter.PythonTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.TxtExporterAdapter
import com.creatorcontenthub.infrastructure.store.InMemoryJobStatusStore
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.metrics.IngestionWindowMetrics
import com.creatorcontenthub.infrastructure.concurrency.SemaphoreConcurrencyController
import com.creatorcontenthub.infrastructure.config.DataSourceFactory
import com.zaxxer.hikari.HikariDataSource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private val log = LoggerFactory.getLogger("JobCleanupScheduler")

fun main() {
    io.ktor.server.netty.EngineMain.main(emptyArray())
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
    // PROCESSAMENTO TEXTO
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
    // INGEST + PIPELINE
    // =============================

    val jobStatusStore = InMemoryJobStatusStore()

    println("DB CONFIG = " + environment.config.propertyOrNull("database.url")?.getString())

    // 🔥 ADAPTAÇÃO CORRETA (PORT)
    val dataSource = DataSourceFactory.create(environment.config)
    val jobRepository = PostgresJobRepository(dataSource)
    // 👉 REGISTRAR HOOK DE SHUTDOWN
    environment.monitor.subscribe(ApplicationStopping) {
        (dataSource as? HikariDataSource)?.close()
    }



    val ingestionMetrics = IngestionMetrics()
    val ingestionWindowMetrics = IngestionWindowMetrics()

    val videoIngestionAdapter = YtDlpVideoIngestionAdapter()
    val transcriptionAdapter = WhisperTranscriptionAdapter()
    val summarizationAdapter = FallbackSummarizationAdapter()

    // =============================
    // CONCORRÊNCIA / BACKPRESSURE
    // =============================

    val maxConcurrentJobs = 4
    val acquireTimeoutMillis = 0L

    val concurrencyController = SemaphoreConcurrencyController(maxConcurrentJobs)

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val ingestYoutubeUseCase = IngestYoutubeUseCase(
        videoIngestionAdapter,
        transcriptionAdapter,
        summarizationAdapter,
        jobRepository, // ✅ CORRETO
        ingestionMetrics,
        concurrencyController,
        acquireTimeoutMillis,
        applicationScope
    )

    // =============================
    // SCHEDULER (TTL CLEANUP)
    // =============================

    environment.monitor.subscribe(ApplicationStopped) {
        log.info("Shutting down application scope...")
        applicationScope.cancel()
    }

    // =============================
    // ROUTING
    // =============================

    configureRouting(
        processTextUseCase,
        exportTextUseCase,
        ingestYoutubeUseCase,
        jobRepository,
        ingestionMetrics,
        ingestionWindowMetrics
    )
}

// =============================
// LOGGING
// =============================

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

// =============================
// SERIALIZAÇÃO
// =============================

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

// =============================
// ROUTING
// =============================

fun Application.configureRouting(
    processTextUseCase: ProcessTextUseCase,
    exportTextUseCase: ExportTextUseCase,
    ingestYoutubeUseCase: IngestYoutubeUseCase,
    jobRepository: JobRepository,
    ingestionMetrics: IngestionMetrics,
    ingestionWindowMetrics: IngestionWindowMetrics
) {
    routing {
        healthRoutes()
        textRoutes(processTextUseCase)
        exportRoutes(exportTextUseCase)

        ingestRoutes(
            ingestYoutubeUseCase,
            jobRepository // ✔ usar Postgres
        )

        metricsRoutes(
            ingestionMetrics,
            ingestionWindowMetrics
        )
    }
}