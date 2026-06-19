package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.ExportTextRequest
import com.creatorcontenthub.application.dto.RangeExportRequest
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

private fun formatTimestamp(
    seconds: Double
): String {

    val totalSeconds =
        seconds.toInt()

    val minutes =
        totalSeconds / 60

    val remainingSeconds =
        totalSeconds % 60

    return "%02d:%02d".format(
        minutes,
        remainingSeconds
    )
}

private fun sanitizeFilename(
    title: String?,
    jobId: String
): String{
    return title
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
}

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

    post("/jobs/{jobId}/export/ranges/txt") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "jobId is required"
            )
            return@post
        }

        val request = call.receive<RangeExportRequest>()

        if (request.ranges.isEmpty()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "ranges are required"
            )
            return@post
        }

        val job = jobRepository.findById(jobId)

        if (job == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Job not found"
            )
            return@post
        }

        val transcript = job.transcript

        if (transcript == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Transcript not available"
            )
            return@post
        }

        if (
            request.ranges.any { range ->

                range.startIndex < 0 ||
                        range.endIndex < 0 ||
                        range.startIndex > range.endIndex ||
                        range.endIndex >= transcript.segments.size
            }
        ) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "Invalid range indexes"
            )
            return@post
        }

        val content =
            request.ranges.mapIndexed { index, range ->

                val selectedSegments =
                    transcript.segments
                        .subList(
                            range.startIndex,
                            range.endIndex + 1
                        )

                val start =
                    selectedSegments.first().start

                val end =
                    selectedSegments.last().end

                val duration =
                    end - start

                val text =
                    selectedSegments.joinToString(" ") {
                        it.text.trim()
                    }

                """
========================================

CORTE #${index + 1}
Início: ${formatTimestamp(start)} | Fim: ${formatTimestamp(end)} | Duração: ${formatTimestamp(duration)}

$text

----------------------------------------
"""
            }.joinToString("\n")

        val exportBaseFilename =
            sanitizeFilename(
                title = job.title,
                jobId = jobId
            )

        val filename =
            "${exportBaseFilename} - Cortes.txt"

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
            contentType =
                ContentType.Text.Plain
                    .withCharset(Charsets.UTF_8)
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
            sanitizeFilename(
                title = job.title,
                jobId = jobId
            )

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
            sanitizeFilename(
                title = job.title,
                jobId = jobId
            )

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