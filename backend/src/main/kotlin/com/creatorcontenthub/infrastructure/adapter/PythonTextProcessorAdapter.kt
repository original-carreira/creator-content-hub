package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.application.port.TextProcessingPort
import java.net.HttpURLConnection
import java.net.URL

class PythonTextProcessorAdapter(
    private val endpoint: String
) : TextProcessingPort {

    override fun process(text: String): String {
        val response = callPython(text)
        return extractProcessedText(response)
    }

    private fun callPython(text: String): String {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 3000
        connection.readTimeout = 5000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val safeText = text.replace("\"", "\\\"")

        val body = """{"text":"$safeText"}"""

        connection.outputStream.use {
            it.write(body.toByteArray())
        }

        val status = connection.responseCode

        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream.bufferedReader().readText()

        if (status !in 200..299) {
            throw RuntimeException("Python service error: HTTP $status - $response")
        }

        return response
    }

    private fun extractProcessedText(raw: String): String {
        // 🔥 Ajuste simples (sem lib JSON ainda)
        return Regex("\"result\"\\s*:\\s*\"(.*?)\"")
            .find(raw)
            ?.groupValues?.get(1)
            ?: raw
    }
}