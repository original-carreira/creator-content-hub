package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.TextExportPort

class TxtExporterAdapter : TextExportPort {

    override fun export(text: String): String {
        return buildString {
            appendLine("=== EXPORT START ===")
            appendLine()
            appendLine(text.trim())
            appendLine()
            appendLine("=== EXPORT END ===")
        }
    }
}