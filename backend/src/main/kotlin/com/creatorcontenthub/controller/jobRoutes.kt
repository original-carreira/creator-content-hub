package com.creatorcontenthub.controller

import com.creatorcontenthub.application.dto.AssetResponse
import com.creatorcontenthub.application.dto.ExportMaterializedSelectionRequest
import com.creatorcontenthub.application.dto.JobResponse
import com.creatorcontenthub.application.dto.TranscriptResponse
import com.creatorcontenthub.application.dto.TranscriptSegmentResponse
import com.creatorcontenthub.application.usecase.ExportAssetIsNotVideoException
import com.creatorcontenthub.application.usecase.ExportJobNotFoundException
import com.creatorcontenthub.application.usecase.ExportMaterializedSelectionUseCase
import com.creatorcontenthub.application.usecase.ExportVideoAssetNotFoundException
import com.creatorcontenthub.application.usecase.ExportVideoSourceUnavailableException
import com.creatorcontenthub.application.usecase.InvalidVideoClipRangeException
import com.creatorcontenthub.application.usecase.VideoClipExportProcessingException
import com.creatorcontenthub.application.usecase.VideoClipOutsideSourceException
import com.creatorcontenthub.application.port.AssetRepository
import com.creatorcontenthub.application.port.JobRepository
import com.creatorcontenthub.domain.model.AssetType
import com.creatorcontenthub.infrastructure.files.FilenameSanitizer
import com.creatorcontenthub.infrastructure.http.respondError
import com.creatorcontenthub.infrastructure.http.respondSuccess
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.response.respond
import io.ktor.server.request.receive
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.io.InputStream
import java.math.BigDecimal
import java.io.File
import org.slf4j.LoggerFactory

fun Route.jobRoutes(
    repository: JobRepository,
    assetRepository: AssetRepository,
    exportMaterializedSelectionUseCase: ExportMaterializedSelectionUseCase
) {

    val logger = LoggerFactory.getLogger("JobRoutes")

    get("/jobs/{jobId}") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = repository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val response = JobResponse(
            status = job.status.name,
            createdAt = job.createdAt,
            startedAt = job.startedAt,
            finishedAt = job.finishedAt,
            title = job.title,
            thumbnailUrl = job.thumbnailUrl,
            transcription = job.transcription,
            summary = job.summary,
            audioAvailable =
                !job.audioPath.isNullOrBlank(),
            videoAvailable =
                !job.videoPath.isNullOrBlank()
        )

        call.respondSuccess(response)
    }

    get("/jobs/{jobId}/assets") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "jobId is required"
            )
            return@get
        }

        val assets =
            assetRepository.findByJobId(
                jobId
            )

        val response =
            assets.map {
                AssetResponse(
                    assetId = it.assetId,
                    assetType = it.assetType.name,
                    createdAt = it.createdAt
                )
            }

        call.respondSuccess(
            response
        )
    }

    get("/jobs/{jobId}/download/transcription") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = repository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val path = job.transcriptionPath

        if (path.isNullOrBlank()) {
            call.respondError(HttpStatusCode.NotFound, "Transcription not available")
            return@get
        }

        val file = File(path)

        if (!file.exists()) {
            call.respondError(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"transcription.txt\""
        )

        call.respondFile(file)

        logger.info("event=file_download_requested jobId={} type=transcription", jobId)
    }

    get("/jobs/{jobId}/download/summary") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = repository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val path = job.summaryPath

        if (path.isNullOrBlank()) {
            call.respondError(HttpStatusCode.NotFound, "Summary not available")
            return@get
        }

        val file = java.io.File(path)

        if (!file.exists()) {
            call.respondError(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        call.response.header(
            io.ktor.http.HttpHeaders.ContentDisposition,
            "attachment; filename=\"summary.txt\""
        )

        call.respondFile(file)

        logger.info("event=file_download_requested jobId={} type=summary", jobId)
    }

    get("/jobs/{jobId}/transcript") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "jobId is required"
            )
            return@get
        }

        val job = repository.findById(jobId)

        if (job == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Job not found"
            )
            return@get
        }

        val transcript = job.transcript

        if (transcript == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Transcript not available"
            )
            return@get
        }

        val response =
            TranscriptResponse(
                text = transcript.text,
                segments =
                    transcript.segments.map {
                        TranscriptSegmentResponse(
                            start = it.start,
                            end = it.end,
                            text = it.text
                        )
                    }
            )

        call.respondSuccess(response)
    }

    get("/jobs/{jobId}/assets/{assetId}/download") {

        val jobId = call.parameters["jobId"]
        val assetId = call.parameters["assetId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        if (assetId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "assetId is required")
            return@get
        }

        val asset =
            assetRepository.findById(
                assetId
            )

        if (asset == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Asset not found"
            )
            return@get
        }

        if (asset.jobId != jobId) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Asset not found"
            )
            return@get
        }

        val job =
            repository.findById(
                jobId
            )

        if (job == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Job not found"
            )
            return@get
        }

        val exportBaseFilename =
            FilenameSanitizer.sanitizeFilename(
                title = job.title,
                jobId = jobId
            )

        val downloadFilename =
            when (asset.assetType) {

                AssetType.VIDEO ->
                    "$exportBaseFilename.mp4"

                AssetType.AUDIO ->
                    "$exportBaseFilename.mp3"

                AssetType.TRANSCRIPT ->
                    "$exportBaseFilename - Transcrição.txt"

                AssetType.SUMMARY ->
                    "$exportBaseFilename - Resumo.txt"
            }

        val file =
            File(
                asset.storagePath
            )

        if (!file.exists()) {
            call.respondError(
                HttpStatusCode.NotFound,
                "File not found"
            )
            return@get
        }

        call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"$downloadFilename\""
        )

        call.respondFile(file)

        logger.info(
            "event=asset_download_requested jobId={} assetId={}",
            jobId,
            assetId
        )
    }

    post("/jobs/{jobId}/assets/{assetId}/selections/export") {

        val jobId = call.parameters["jobId"]
        val assetId = call.parameters["assetId"]

        if (jobId.isNullOrBlank() || assetId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "jobId and assetId are required"
            )
            return@post
        }

        val request = try {
            call.receive<ExportMaterializedSelectionRequest>()
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (_: Exception) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "Invalid selection export request"
            )
            return@post
        }

        if (
            request.selectionId.isBlank() ||
            request.selectionId.length > 100
        ) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "selectionId is required and must be at most 100 characters"
            )
            return@post
        }

        val outputFile = try {
            exportMaterializedSelectionUseCase.execute(
                jobId = jobId,
                assetId = assetId,
                startTime = request.startTime,
                endTime = request.endTime
            )
        } catch (_: ExportJobNotFoundException) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@post
        } catch (_: ExportVideoAssetNotFoundException) {
            call.respondError(HttpStatusCode.NotFound, "Video asset not found")
            return@post
        } catch (_: ExportAssetIsNotVideoException) {
            call.respondError(HttpStatusCode.BadRequest, "Asset is not a VIDEO")
            return@post
        } catch (_: ExportVideoSourceUnavailableException) {
            call.respondError(HttpStatusCode.NotFound, "Video source is unavailable")
            return@post
        } catch (_: InvalidVideoClipRangeException) {
            call.respondError(HttpStatusCode.BadRequest, "Invalid selection interval")
            return@post
        } catch (_: VideoClipOutsideSourceException) {
            call.respondError(HttpStatusCode.BadRequest, "Selection is outside video duration")
            return@post
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (exception: VideoClipExportProcessingException) {
            logger.error(
                "event=video_clip_export_failed jobId={} assetId={} selectionId={} message={}",
                jobId,
                assetId,
                request.selectionId,
                exception.message
            )
            call.respondError(
                HttpStatusCode.InternalServerError,
                "Could not export the selected video interval"
            )
            return@post
        }

        val filename = buildSelectionExportFilename(
            jobId = jobId,
            selectionId = request.selectionId,
            startTime = request.startTime,
            endTime = request.endTime
        )

        try {
            call.respond(
                TemporaryMp4Content(
                    file = outputFile,
                    filename = filename
                )
            )
        } catch (exception: Throwable) {
            outputFile.delete()
            throw exception
        }

        logger.info(
            "event=video_clip_export_response_started jobId={} assetId={} selectionId={} start={} end={}",
            jobId,
            assetId,
            request.selectionId,
            request.startTime,
            request.endTime
        )
    }

    get("/jobs/{jobId}/assets/{assetId}/stream") {

        val jobId =
            call.parameters["jobId"]

        val assetId =
            call.parameters["assetId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "jobId is required"
            )
            return@get
        }

        if (assetId.isNullOrBlank()) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "assetId is required"
            )
            return@get
        }

        val asset =
            assetRepository.findById(
                assetId
            )

        if (asset == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Asset not found"
            )
            return@get
        }

        if (asset.jobId != jobId) {
            call.respondError(
                HttpStatusCode.NotFound,
                "Asset not found"
            )
            return@get
        }

        if (asset.assetType != AssetType.VIDEO) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "Asset is not a VIDEO"
            )
            return@get
        }

        val file =
            File(
                asset.storagePath
            )

        if (!file.exists()) {
            call.respondError(
                HttpStatusCode.NotFound,
                "File not found"
            )
            return@get
        }

        call.respondFile(
            file
        )

        logger.info(
            "event=video_stream_requested jobId={} assetId={}",
            jobId,
            assetId
        )
    }

    get("/jobs/{jobId}/preview") {

        val jobId = call.parameters["jobId"]

        if (jobId.isNullOrBlank()) {
            call.respondError(HttpStatusCode.BadRequest, "jobId is required")
            return@get
        }

        val job = repository.findById(jobId)

        if (job == null) {
            call.respondError(HttpStatusCode.NotFound, "Job not found")
            return@get
        }

        val transcriptionPath = job.transcriptionPath
        val summaryPath = job.summaryPath

        if (transcriptionPath.isNullOrBlank() || summaryPath.isNullOrBlank()) {
            call.respondError(HttpStatusCode.NotFound, "Preview not available")
            return@get
        }

        val transcriptionFile = java.io.File(transcriptionPath)
        val summaryFile = java.io.File(summaryPath)

        if (!transcriptionFile.exists() || !summaryFile.exists()) {
            call.respondError(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        val transcriptionContent = transcriptionFile.readText(Charsets.UTF_8)
        val summaryContent = summaryFile.readText(Charsets.UTF_8)

        val response = mapOf(
            "jobId" to jobId,
            "transcription" to transcriptionContent,
            "summary" to summaryContent
        )

        call.respondSuccess(response)

        logger.info("event=preview_requested jobId={}", jobId)
    }

}

private fun buildSelectionExportFilename(
    jobId: String,
    selectionId: String,
    startTime: Double,
    endTime: Double
): String {

    fun formatTime(value: Double): String =
        value.toLong()
            .let { totalSeconds ->
                val hours = totalSeconds / 3_600
                val minutes = (totalSeconds % 3_600) / 60
                val seconds = totalSeconds % 60
                "%02dh%02dm%02ds".format(hours, minutes, seconds)
            }

    val exportName =
        "Selecao_${selectionId}_${formatTime(startTime)}_a_${formatTime(endTime)}"

    return "${FilenameSanitizer.sanitizeFilename(exportName, jobId)}.mp4"
}

private class TemporaryMp4Content(
    private val file: File,
    private val filename: String
) : OutgoingContent.WriteChannelContent() {

    private val size = file.length()

    override val contentType =
        ContentType.parse("video/mp4")

    override val contentLength = size

    override val headers =
        io.ktor.http.Headers.build {
            append(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(
                        ContentDisposition.Parameters.FileName,
                        filename
                    )
                    .toString()
            )
            append(HttpHeaders.CacheControl, "no-store")
        }

    override suspend fun writeTo(
        channel: ByteWriteChannel
    ) {

        try {
            file.inputStream().use { input ->
                copyToChannel(
                    input = input,
                    channel = channel
                )
            }
            channel.flush()
        } finally {
            if (!file.delete()) {
                file.deleteOnExit()
            }
        }

    }

    private suspend fun copyToChannel(
        input: InputStream,
        channel: ByteWriteChannel
    ) {

        val buffer = ByteArray(64 * 1024)

        while (true) {
            val bytesRead = input.read(buffer)

            if (bytesRead < 0) {
                return
            }

            channel.writeFully(
                buffer,
                offset = 0,
                length = bytesRead
            )
        }

    }
}
