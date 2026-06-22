package com.creatorcontenthub.infrastructure.files

object FilenameSanitizer {

    fun sanitizeFilename(
        title: String?,
        jobId: String
    ): String {

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
}