package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.AudioGenerationPort
import com.creatorcontenthub.infrastructure.storage.FileStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

class FFmpegAudioGenerationAdapter(
    private val fileStorageService: FileStorageService,
) : AudioGenerationPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun generateAudio(
        videoPath: String,
        jobId: String
    ): String = withContext(Dispatchers.IO) {

        logger.info(
            "event=audio_generation_started jobId={} videoPath={}",
            jobId,
            videoPath
        )

        val sourceVideo = File(videoPath)

        require(sourceVideo.exists()) {
            "Video file not found: $videoPath"
        }

        val tempAudio = File.createTempFile(
            "audio-$jobId",
            ".mp3"
        )

        logger.info(
            "event=audio_temp_created jobId={} tempAudio={}",
            jobId,
            tempAudio.absolutePath
        )

        logger.info(
            "event=ffmpeg_starting jobId={} source={}",
            jobId,
            sourceVideo.absolutePath
        )

        val process = ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i",
            sourceVideo.absolutePath,
            "-vn",
            "-acodec",
            "libmp3lame",
            tempAudio.absolutePath
        )
            .redirectErrorStream(true)
            .start()

        logger.info(
            "event=ffmpeg_waiting jobId={}",
            jobId
        )

        // Consome o output stream de forma assíncrona sem criar threads pesadas do sistema operacional
        val readerJob = launch {
            process.inputStream
                .bufferedReader()
                .forEachLine { line ->
                    logger.info(
                        "event=ffmpeg_output_line jobId={} line={}",
                        jobId,
                        line
                    )
                }
        }

        // Aguarda a finalização do processo de forma segura no Dispatchers.IO
        val exitCode = process.waitFor()

        // Garante que a leitura do log terminou antes de prosseguir
        readerJob.join()

        logger.info(
            "event=ffmpeg_finished jobId={} exitCode={}",
            jobId,
            exitCode
        )

        require(exitCode == 0) {
            "FFmpeg failed with exit code $exitCode"
        }

        logger.info(
            "event=audio_asset_persisting jobId={} tempAudio={}",
            jobId,
            tempAudio.absolutePath
        )

        return@withContext fileStorageService.saveAsset(
            jobId = jobId,
            assetType = "audio",
            sourcePath = tempAudio.absolutePath,
            fileName = "$jobId.mp3"
        )
    }
}
