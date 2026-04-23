package com.creatorcontenthub.application.port

import com.creatorcontenthub.application.dto.SummarizationResult

interface SummarizationPort {
    fun summarize(input: String): SummarizationResult
}