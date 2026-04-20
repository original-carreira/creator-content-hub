package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.TextExportPort

class ExportTextUseCase(
    private val exporter: TextExportPort
) {

    fun execute(text: String): String {
        return exporter.export(text)
    }
}