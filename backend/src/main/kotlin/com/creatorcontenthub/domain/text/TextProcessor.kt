package com.creatorcontenthub.domain.text

class TextProcessor {

    fun process(text: String): String {
        var result = text
        result = normalize(result)
        result = removeTranscriptionMarkers(result)
        result = removeRepetitions(result)
        result = removeNoise(result)
        result = removeRedundancy(result)
        return result
    }

    private fun normalize(text: String): String {
        return Regex("\\s+").replace(text.trim()," ")
    }

    private fun removeTranscriptionMarkers(text: String): String {
        return Regex("\\[(.*?)\\]").replace(text,"").trim()
    }

    private fun removeRepetitions(text: String): String {
        return Regex("\\b(\\w+)\\s+\\1\\b", RegexOption.IGNORE_CASE)
            .replace(text, "$1")
    }

    private fun removeNoise(text: String): String {
        return removeRepetitions(text)
    }

    private fun removeRedundancy(text: String): String {
        val tokens = text.split(Regex("\\s+"))
        val resultados = mutableListOf<String>()

        for (i in tokens.indices) {
            if (i == 0 || tokens[i].lowercase() != tokens[i -1].lowercase()) {
                resultados.add(tokens[i])
            }
        }
        return resultados.joinToString(" ")
    }
}