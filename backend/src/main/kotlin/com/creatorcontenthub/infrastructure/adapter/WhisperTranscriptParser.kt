package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.domain.model.Transcript
import com.creatorcontenthub.domain.model.TranscriptSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WhisperTranscriptParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
    }

    fun parse(json: String): Transcript {

        val payload =
            jsonParser.decodeFromString<WhisperJson>(json)

        return Transcript(
            text = payload.text.trim(),
            segments = payload.segments.map {
                TranscriptSegment(
                    start = it.start,
                    end = it.end,
                    text = it.text.trim()
                )
            }
        )
    }

    @Serializable
    private data class WhisperJson(
        val text: String,
        val segments: List<WhisperSegment>
    )

    @Serializable
    private data class WhisperSegment(
        val start: Double,
        val end: Double,
        val text: String
    )
}