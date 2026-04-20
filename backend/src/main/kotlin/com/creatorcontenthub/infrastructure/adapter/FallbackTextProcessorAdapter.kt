package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.TextProcessingPort

class FallbackTextProcessorAdapter(
    private val primary: TextProcessingPort,
    private val fallback: TextProcessingPort
) : TextProcessingPort {

    override fun process(text: String): String {
        return try {
            primary.process(text)
        } catch (ex: Exception) {
            fallback.process(text)
        }
    }
}