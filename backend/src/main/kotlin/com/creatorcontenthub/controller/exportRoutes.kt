package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.ExportTextRequest
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.application.usecase.ExportTextUseCase
import com.creatorcontenthub.infrastructure.http.respondError
import org.slf4j.LoggerFactory
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentDisposition
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.io.FileOutputStream
import io.ktor.server.response.respondFile
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.ParagraphAlignment

fun Route.exportRoutes(
    exportUseCase: ExportTextUseCase,
    jobRepository: JobRepository
) {
    val logger = LoggerFactory.getLogger("ExportRoutes")

    post("/export/txt") {

        val request = call.receive<ExportTextRequest>()

        // 🔹 Validação (mantida)
        if (request.text.isBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "text is required",
                "INVALID_REQUEST"
            )
            return@post
        }

        val result = exportUseCase.execute(request.text)

        // 🔹 Nome do arquivo (preparado para evolução futura)
        val filename = "output.txt"

        // 🔹 Header robusto (compatível com UTF-8 e browsers modernos)
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, filename)
                .withParameter("filename*", "UTF-8''$filename")
                .toString()
        )

        // 🔹 Resposta como arquivo texto
        call.respondText(
            text = result,
            contentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8)
        )
    }

    get("/jobs/{jobId}/export/txt") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = jobRepository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val transcriptionPath = job.transcriptionPath
        val summaryPath = job.summaryPath

        if (transcriptionPath.isNullOrBlank() || summaryPath.isNullOrBlank()) {
            call.respondError(HttpStatusCode.NotFound, "Export not available")
            return@get
        }

        val transcriptionFile = java.io.File(transcriptionPath)
        val summaryFile = java.io.File(summaryPath)

        if (!transcriptionFile.exists() || !summaryFile.exists()) {
            call.respondError(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        val content = """
        ===== TRANSCRIPTION =====

        ${transcriptionFile.readText(Charsets.UTF_8)}

        ===== SUMMARY =====

        ${summaryFile.readText(Charsets.UTF_8)}
    """.trimIndent()

        val exportBaseFilename =
            job.title
                ?.trim()
                ?.replace(Regex("[\\\\/:*?\"<>|]"), "")
                ?.replace(Regex("[“”‘’]"), "")
                ?.replace(Regex("[\\p{So}\\p{Cn}]"), "")
                ?.replace(Regex("(^|\\s)_([^_]+)_(?=\\s|$)"), "$1$2")
                ?.replace(Regex("^[_\\-.\\s]+"), "")
                ?.replace(Regex("[_\\-.\\s]+$"), "")
                ?.replace(Regex("\\s+"), " ")
                ?.ifBlank { null }
                ?: "job_$jobId"

        val filename =
            "${exportBaseFilename}.txt"

        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(
                    ContentDisposition.Parameters.FileName,
                    filename
                )
                .withParameter(
                    "filename*",
                    "UTF-8''$filename"
                )
                .toString()
        )

        call.respondText(
            text = content,
            contentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8)
        )

        logger.info("event=file_export_requested jobId={} type=txt", jobId)
    }

    get("/jobs/{jobId}/export/docx") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = jobRepository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val exportBaseFilename =
            job.title
                ?.trim()
                ?.replace(Regex("[\\\\/:*?\"<>|]"), "")
                ?.replace(Regex("[“”‘’]"), "")
                ?.replace(Regex("[\\p{So}\\p{Cn}]"), "")
                ?.replace(Regex("(^|\\s)_([^_]+)_(?=\\s|$)"), "$1$2")
                ?.replace(Regex("^[_\\-.\\s]+"), "")
                ?.replace(Regex("[_\\-.\\s]+$"), "")
                ?.replace(Regex("\\s+"), " ")
                ?.ifBlank { null }
                ?: "job_$jobId"

        val transcriptionPath = job.transcriptionPath
        val summaryPath = job.summaryPath

        if (transcriptionPath.isNullOrBlank() || summaryPath.isNullOrBlank()) {
            call.respondError(HttpStatusCode.NotFound, "Export not available")
            return@get
        }

        val transcriptionFile = File(transcriptionPath)
        val summaryFile = File(summaryPath)

        if (!transcriptionFile.exists() || !summaryFile.exists()) {
            call.respondError(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        val transcriptionText = transcriptionFile.readText(Charsets.UTF_8)
        val summaryText = summaryFile.readText(Charsets.UTF_8)

        // ===== gerar DOCX =====
        val doc = XWPFDocument()

        // Titulo
        doc.createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            createRun().apply {
                isBold = true
                fontSize = 16
                setText("Creator Content Hub Export")
            }
        }

        // Espaco
        doc.createParagraph()

        // Secao transcription
        doc.createParagraph().createRun().apply {
            isBold = true
            setText("TRANSCRIPTION")
        }

        doc.createParagraph().createRun().setText(transcriptionText)

        // Espaco
        doc.createParagraph()

        // Secao summary
        doc.createParagraph().createRun().apply {
            isBold = true
            setText("SUMMARY")
        }

        doc.createParagraph().createRun().setText(summaryText)

        // arquivo temporario
        val tempFile = File.createTempFile("job_$jobId", ".docx")
        tempFile.deleteOnExit()

        FileOutputStream(tempFile).use { out ->
            doc.write(out)
        }
        doc.close()

        val docxFilename =
            "${exportBaseFilename}.docx"

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(
                    ContentDisposition.Parameters.FileName,
                    docxFilename
                )
                .withParameter(
                    "filename*",
                    "UTF-8''$docxFilename"
                )
                .toString()
        )

        call.respondFile(tempFile)

        logger.info("event=file_export_requested jobId={} type=docx", jobId)
    }
}