package com.creatorcontenthub.infrastructure.search

object SearchQueryBuilder {

    fun sanitizeInput(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^a-z0-9à-ú\\s]"), " ")
            .trim()
    }

    fun toPrefixTsQuery(input: String): String {
        val sanitized = sanitizeInput(input)

        if (sanitized.isBlank()) return ""

        return sanitized
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" & ") { "$it:*" }
    }

    fun toOrTsQuery(input: String): String {
        val sanitized = sanitizeInput(input)

        if (sanitized.isBlank()) return ""

        return sanitized
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" | ") { "$it:*" }
    }
}