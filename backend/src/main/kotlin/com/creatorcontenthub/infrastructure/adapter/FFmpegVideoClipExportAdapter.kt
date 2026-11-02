package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.VideoClipExportPort
import com.creatorcontenthub.application.usecase.VideoClipExportProcessingException
import com.creatorcontenthub.application.usecase.VideoClipOutsideSourceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class FFmpegVideoClipExportAdapter(
    private val tempDirectory: File,
    private val ffmpegCommand: String = "ffmpeg",
    private val ffprobeCommand: String = "ffprobe",
    private val processTimeoutSeconds: Long = 300
) : VideoClipExportPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun export(
        sourceVideo: File,
        startTime: Double,
        endTime: Double
    ): File = withContext(Dispatchers.IO) {

        if (
            !sourceVideo.isFile ||
            !sourceVideo.canRead()
        ) {
            throw VideoClipExportProcessingException(
                "Source video is not readable"
            )
        }

        val sourceDuration =
            probeDuration(sourceVideo)

        if (
            endTime > sourceDuration + DURATION_TOLERANCE_SECONDS
        ) {
            throw VideoClipOutsideSourceException()
        }

        if (
            !tempDirectory.exists() &&
            !tempDirectory.mkdirs()
        ) {
            throw VideoClipExportProcessingException(
                "Could not create the video export temp directory"
            )
        }

        val outputFile = try {
            File.createTempFile(
                "selection-clip-",
                ".mp4",
                tempDirectory
            )
        } catch (exception: IOException) {
            throw VideoClipExportProcessingException(
                "Could not create the temporary MP4",
                exception
            )
        }

        outputFile.deleteOnExit()

        try {
            val duration = endTime - startTime

            runProcess(
                listOf(
                    ffmpegCommand,
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-ss", startTime.toString(),
                    "-i", sourceVideo.absolutePath,
                    "-t", duration.toString(),
                    "-map", "0:v:0",
                    "-map", "0:a:0?",
                    "-c:v", "libx264",
                    "-preset", "medium",
                    "-crf", "16",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-movflags", "+faststart",
                    "-f", "mp4",
                    outputFile.absolutePath
                ),
                "ffmpeg"
            )

            if (
                !outputFile.isFile ||
                outputFile.length() == 0L
            ) {
                throw VideoClipExportProcessingException(
                    "FFmpeg produced an empty MP4"
                )
            }

            logger.info(
                "event=video_clip_export_ready source={} start={} end={} outputBytes={}",
                sourceVideo.absolutePath,
                startTime,
                endTime,
                outputFile.length()
            )

            outputFile

        } catch (exception: CancellationException) {
            outputFile.delete()
            throw exception
        } catch (exception: Exception) {
            outputFile.delete()
            throw exception
        }
    }

    private suspend fun probeDuration(
        sourceVideo: File
    ): Double {

        val output = runProcess(
            listOf(
                ffprobeCommand,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                sourceVideo.absolutePath
            ),
            "ffprobe"
        )

        val duration =
            output.trim().toDoubleOrNull()

        if (
            duration == null ||
            !duration.isFinite() ||
            duration <= 0.0
        ) {
            throw VideoClipExportProcessingException(
                "Could not determine source video duration"
            )
        }

        return duration
    }

    private suspend fun runProcess(
        command: List<String>,
        processName: String
    ): String {

        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            throw VideoClipExportProcessingException(
                "Could not start $processName",
                exception
            )
        }

        val output = StringBuilder()
        val outputLock = Any()

        val outputReader = Thread {
            try {
                process.inputStream
                    .bufferedReader()
                    .useLines { lines ->
                        lines.forEach { line ->
                            synchronized(outputLock) {
                                if (output.length < MAX_CAPTURED_OUTPUT_CHARS) {
                                    output.append(line)
                                        .append('\n')
                                }
                            }
                        }
                    }
            } catch (_: IOException) {
                // The process may close its stream while cancellation is in progress.
            }
        }.apply {
            name = "$processName-output-reader"
            isDaemon = true
            start()
        }

        val deadlineNanos =
            System.nanoTime() +
                TimeUnit.SECONDS.toNanos(
                    processTimeoutSeconds
                )

        try {
            var finished = false

            while (!finished) {
                currentCoroutineContext().ensureActive()

                val remainingNanos =
                    deadlineNanos - System.nanoTime()

                if (remainingNanos <= 0L) {
                    throw VideoClipExportProcessingException(
                        "$processName timed out after $processTimeoutSeconds seconds"
                    )
                }

                val waitMillis =
                    minOf(
                        250L,
                        TimeUnit.NANOSECONDS.toMillis(
                            remainingNanos
                        ).coerceAtLeast(1L)
                    )

                finished = process.waitFor(
                    waitMillis,
                    TimeUnit.MILLISECONDS
                )
            }

            outputReader.join(5_000)

            if (outputReader.isAlive) {
                outputReader.interrupt()
            }

            val outputText = synchronized(outputLock) {
                output.toString()
            }

            if (process.exitValue() != 0) {
                throw VideoClipExportProcessingException(
                    "$processName exited with code ${process.exitValue()}: ${outputText.takeLast(2_000)}"
                )
            }

            return outputText

        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: VideoClipExportProcessingException) {
            throw exception
        } catch (exception: Exception) {
            throw VideoClipExportProcessingException(
                "$processName failed",
                exception
            )
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }

            outputReader.interrupt()
        }
    }

    private companion object {
        const val DURATION_TOLERANCE_SECONDS = 0.001
        const val MAX_CAPTURED_OUTPUT_CHARS = 32_000
    }
}
