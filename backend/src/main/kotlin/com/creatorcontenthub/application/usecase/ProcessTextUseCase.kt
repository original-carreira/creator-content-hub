package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.application.port.TextProcessingPort

class ProcessTextUseCase(
    private val processor: TextProcessingPort
) {
    fun execute(text: String): String {
        return processor.process(text)
    }
}