package com.creatorcontenthub.application.port

interface TextExportPort {
    fun export(text: String): String
}