package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.SummarizationResult
import com.creatorcontenthub.application.port.SummarizationPort
import java.time.Instant

class FallbackSummarizationAdapter : SummarizationPort {

    companion object {
        private const val MAX = 1000
    }

    override fun summarize(input: String): SummarizationResult {

        if (input.isBlank()) {
            return SummarizationResult(
                summary = "Sem conteúdo para resumir",
                generatedAt = Instant.now(),
                inputTruncated = false
            )
        }

        val truncated = input.length > MAX
        val safe = input.take(MAX)

        return SummarizationResult(
            summary = safe,
            generatedAt = Instant.now(),
            inputTruncated = truncated
        )
    }
}