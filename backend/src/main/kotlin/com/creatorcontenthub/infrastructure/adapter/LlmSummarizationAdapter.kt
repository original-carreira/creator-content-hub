package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.dto.SummarizationResult
import com.creatorcontenthub.application.port.SummarizationPort
import java.time.Instant

class LlmSummarizationAdapter : SummarizationPort {

    companion object {
        private const val MAX_INPUT = 10_000
    }

    override fun summarize(input: String): SummarizationResult {

        val (safeInput, truncated) = truncate(input)

        val summary = callLlm(safeInput)

        require(summary.isNotBlank()) {
            "LLM returned empty summary"
        }

        return SummarizationResult(
            summary = summary,
            generatedAt = Instant.now(),
            inputTruncated = truncated
        )
    }

    private fun truncate(input: String): Pair<String, Boolean> {
        if (input.length <= MAX_INPUT) return input to false

        val cut = input.take(MAX_INPUT)
        val lastSpace = cut.lastIndexOf(' ')
        val result = if (lastSpace > 0) cut.substring(0, lastSpace) else cut

        return result to true
    }

    private fun callLlm(text: String): String {
        return "Resumo gerado automaticamente"
    }
}