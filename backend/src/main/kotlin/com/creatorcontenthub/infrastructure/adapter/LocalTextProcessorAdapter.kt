package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.TextProcessingPort
import com.creatorcontenthub.domain.text.TextProcessor

class LocalTextProcessorAdapter : TextProcessingPort {

    private val processor = TextProcessor()

    override fun process(text: String): String {
        return processor.process(text)
    }
}