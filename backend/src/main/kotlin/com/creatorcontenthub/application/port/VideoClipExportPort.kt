package com.creatorcontenthub.application.port

import java.io.File

interface VideoClipExportPort {

    suspend fun export(
        sourceVideo: File,
        startTime: Double,
        endTime: Double
    ): File
}
