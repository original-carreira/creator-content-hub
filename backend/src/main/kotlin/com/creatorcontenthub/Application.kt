package com.creatorcontenthub

import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.usecase.CancelJobUseCase
import com.creatorcontenthub.application.usecase.DeleteJobUseCase
import com.creatorcontenthub.application.usecase.DeleteJobsUseCase
import com.creatorcontenthub.application.pipeline.DownloadStep
import com.creatorcontenthub.application.pipeline.FinalizeJobStep
import com.creatorcontenthub.application.pipeline.TranscriptionStep
import com.creatorcontenthub.application.pipeline.SummaryStep
import com.creatorcontenthub.application.pipeline.JobProcessor
import com.creatorcontenthub.application.service.ExistingJobResolver
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
import com.creatorcontenthub.application.usecase.ListJobsUseCase
import com.creatorcontenthub.application.usecase.ResumeJobUseCase
import com.creatorcontenthub.application.usecase.SearchJobsUseCase
import com.creatorcontenthub.application.worker.QueueWorker
import com.creatorcontenthub.controller.healthDbRoute
import com.creatorcontenthub.controller.jobMutationRoutes
import com.creatorcontenthub.controller.jobRoutes
import com.creatorcontenthub.controller.searchRoutes
import com.creatorcontenthub.infrastructure.adapter.YtDlpVideoIngestionAdapter
import com.creatorcontenthub.infrastructure.adapter.WhisperTranscriptionAdapter
import com.creatorcontenthub.infrastructure.adapter.FallbackSummarizationAdapter
import com.creatorcontenthub.infrastructure.adapter.FallbackTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.LocalTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.PostgresJobQueryRepository
import com.creatorcontenthub.infrastructure.adapter.PostgresJobRepository
import com.creatorcontenthub.infrastructure.adapter.PostgresJobSearchRepository
import com.creatorcontenthub.infrastructure.adapter.PythonTextProcessorAdapter
import com.creatorcontenthub.infrastructure.adapter.TxtExporterAdapter
import com.creatorcontenthub.infrastructure.cleanup.FileCleanupService
import com.creatorcontenthub.infrastructure.metrics.IngestionMetrics
import com.creatorcontenthub.infrastructure.concurrency.SemaphoreConcurrencyController
import com.creatorcontenthub.infrastructure.config.DataSourceFactory
import com.creatorcontenthub.infrastructure.config.IngestionTimeoutConfig
import com.creatorcontenthub.infrastructure.metrics.HikariMetrics
import com.creatorcontenthub.infrastructure.metrics.JvmMetricsConfig
import com.creatorcontenthub.infrastructure.metrics.PrometheusRegistry
import com.creatorcontenthub.infrastructure.metrics.SearchMetrics
import com.creatorcontenthub.infrastructure.storage.FileStorageService
import com.creatorcontenthub.infrastructure.adapter.PostgresJobQueueRepository
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import org.slf4j.LoggerFactory
import org.flywaydb.core.Flyway
import io.ktor.server.request.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File


private val log = LoggerFactory.getLogger("JobCleanupScheduler")

fun main(args: Array<String>) {
    // Isso delega a configuração para o arquivo application.conf
    EngineMain.main(args)
}

fun Application.module() {
    configureRequestId()
    configureLogging()
    configureSerialization()
    configureStatusPages()

    val meterRegistry = PrometheusRegistry.registry
    JvmMetricsConfig.register(meterRegistry)

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

    // ADAPTACAOO CORRETA (PORT)
    val dataSource = DataSourceFactory.create(environment.config)

    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()

    val hikariMetrics = HikariMetrics(dataSource)

    val jobRepository = PostgresJobRepository(dataSource)

    val jobQueueRepository = PostgresJobQueueRepository(dataSource)

    environment.monitor.subscribe(ApplicationStopping) {
        (dataSource as? HikariDataSource)?.close()
    }

    val jobQueryRepository = PostgresJobQueryRepository(dataSource)
    val config = environment.config

    val jobSearchRepository = PostgresJobSearchRepository(
        dataSource = dataSource,
        rankWeight = config.property("search.rank.textWeight").getString().toDouble(),
        timeWeight = config.property("search.rank.timeWeight").getString().toDouble(),
        doneBoost = config.property("search.rank.statusBoost.DONE").getString().toDouble(),
        failedBoost = config.property("search.rank.statusBoost.FAILED").getString().toDouble(),
        defaultBoost = config.property("search.rank.statusBoost.DEFAULT").getString().toDouble(),
        maxScore = config.propertyOrNull("search.rank.maxScore")
            ?.getString()
            ?.toDouble()
            ?: 2.0,
        recencyDecay = config.propertyOrNull("search.rank.recencyDecay")
            ?.getString()
            ?.toDouble()
            ?: 7.0
    )

    val searchMetrics = SearchMetrics(meterRegistry)

    val searchJobsUseCase = SearchJobsUseCase(
        repository = jobSearchRepository,
        searchMetrics = searchMetrics
    )
    val listJobsUseCase = ListJobsUseCase(jobQueryRepository)

    val ingestionMetrics = IngestionMetrics()

    val ytDlpPath = environment.config
        .propertyOrNull("ytDlp.path")
        ?.getString()

    val outputDir = System.getenv("OUTPUT_DIR")
        ?: environment.config.propertyOrNull("app.outputDir")?.getString()
        ?: "C:\\creator-content-hub-data"

    File(outputDir).mkdirs()
    File("$outputDir/audio").mkdirs()
    File("$outputDir/transcription").mkdirs()
    File("$outputDir/temp").mkdirs()

    val fileStorageService = FileStorageService("$outputDir/jobs")

    val videoIngestionAdapter = YtDlpVideoIngestionAdapter(
        configuredPath = ytDlpPath,
        outputDir = outputDir,
        timeoutConfig = IngestionTimeoutConfig(),
        jobRepository = jobRepository
    )

    val cleanupService = FileCleanupService(outputDir)
    cleanupService.start()

    val whisperPath = environment.config
        .propertyOrNull("whisper.path")
        ?.getString()

    val transcriptionAdapter = WhisperTranscriptionAdapter(
        configuredPath = whisperPath,
        timeoutMinutes = 10,
        jobRepository = jobRepository
    )
    val summarizationAdapter = FallbackSummarizationAdapter()

    val downloadStep = DownloadStep(
        ingestionPort = videoIngestionAdapter,
        jobRepository = jobRepository
    )

    val transcriptionStep = TranscriptionStep(
        transcriptionPort = transcriptionAdapter,
        jobRepository = jobRepository,
        fileStorageService = fileStorageService
    )

    val summaryStep = SummaryStep(
        summarizationPort = summarizationAdapter,
        jobRepository = jobRepository,
        fileStorageService = fileStorageService
    )

    val finalizeJobStep = FinalizeJobStep(
        jobRepository = jobRepository
    )

    val existingJobResolver = ExistingJobResolver()

    val jobProcessor = JobProcessor(
        jobRepository = jobRepository,
        steps = listOf(
            downloadStep,
            transcriptionStep,
            summaryStep,
            finalizeJobStep
        )
    )

    val queueWorker = QueueWorker(
        queueRepository = jobQueueRepository,
        jobRepository = jobRepository,
        jobProcessor = jobProcessor
    )

    // =============================
    // CONCORRENCIA / BACKPRESSURE
    // =============================

    val maxConcurrentJobs = 4
    val acquireTimeoutMillis = 0L

    val concurrencyController = SemaphoreConcurrencyController(maxConcurrentJobs)

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    applicationScope.launch {
        queueWorker.start()
    }

    val ingestYoutubeUseCase = IngestYoutubeUseCase(
        videoIngestionAdapter,
        transcriptionAdapter,
        summarizationAdapter,
        jobRepository,
        ingestionMetrics,
        concurrencyController,
        acquireTimeoutMillis,
        com.creatorcontenthub.infrastructure.metrics.IngestionMicrometerMetrics(),
        fileStorageService,
        jobProcessor,
        existingJobResolver,
        jobQueueRepository
    )

    val cancelJobUseCase = CancelJobUseCase(jobRepository)

    val resumeJobUseCase = ResumeJobUseCase(
        repository = jobRepository,
        jobProcessor = jobProcessor,
        scope = applicationScope
    )

    environment.monitor.subscribe(ApplicationStopped) {
        log.info("Shutting down application...")

        cleanupService.stop()
        applicationScope.cancel()
    }

    val deleteJobUseCase = DeleteJobUseCase(jobRepository)
    val deleteJobsUseCase = DeleteJobsUseCase(jobRepository)

    // =============================
    // ROUTING
    // =============================

    configureRouting(
        processTextUseCase,
        exportTextUseCase,
        ingestYoutubeUseCase,
        jobRepository,
        ingestionMetrics,
        hikariMetrics,
        dataSource,
        listJobsUseCase,
        searchJobsUseCase,
        cancelJobUseCase,
        resumeJobUseCase,
        deleteJobUseCase,
        deleteJobsUseCase
    )
}

// =============================
// LOGGING
// =============================

fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            val path = call.request.path()
            path.startsWith("/process") ||
            path.startsWith("/ingest") ||
            path.startsWith("/metrics") ||
            path.startsWith("/health")
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
// SERIALIZACAO
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
    hikariMetrics: HikariMetrics,
    dataSource: HikariDataSource,
    listJobsUseCase: ListJobsUseCase,
    searchJobsUseCase: SearchJobsUseCase,
    cancelJobUseCase: CancelJobUseCase,
    resumeJobUseCase: ResumeJobUseCase,
    deleteJobUseCase: DeleteJobUseCase,
    deleteJobsUseCase: DeleteJobsUseCase,

) {
    routing {
        healthRoutes()
        healthDbRoute(dataSource)
        textRoutes(processTextUseCase)
        exportRoutes(exportTextUseCase, jobRepository)
        searchRoutes(searchJobsUseCase)
        jobRoutes(jobRepository)
        jobMutationRoutes(deleteJobUseCase, deleteJobsUseCase)

        ingestRoutes(
            ingestYoutubeUseCase,
            jobRepository,
            listJobsUseCase,
            cancelJobUseCase,
            resumeJobUseCase

        )

        metricsRoutes(
            ingestionMetrics,
            hikariMetrics
        )

        staticResources("/", "frontend")
    }
}