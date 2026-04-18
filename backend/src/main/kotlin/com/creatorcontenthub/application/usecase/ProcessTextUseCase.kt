package com.creatorcontenthub.application.usecase

import com.creatorcontenthub.domain.text.TextProcessor

class ProcessTextUseCase(
    private val textProcessor: TextProcessor = TextProcessor()
) {
    fun execute(text: String): String {
        return textProcessor.process(text)
    }
}