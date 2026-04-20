package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.TextProcessingPort
import org.slf4j.LoggerFactory

class FallbackTextProcessorAdapter(
    private val primary: TextProcessingPort,
    private val fallback: TextProcessingPort
) : TextProcessingPort {

    private val logger = LoggerFactory.getLogger(FallbackTextProcessorAdapter::class.java)

    override fun process(text: String): String {
        return try {
            primary.process(text)
        } catch (ex: Exception) {
            logger.error("python adapter failed → fallback activated", ex)
            fallback.process(text)
        }
    }
}