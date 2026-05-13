package com.creatorcontenthub.infrastructure.adapter

import com.creatorcontenthub.infrastructure.exception.ExternalServiceException
import com.creatorcontenthub.application.port.TextProcessingPort
import com.creatorcontenthub.infrastructure.http.pythonCalls
import com.creatorcontenthub.infrastructure.http.pythonErrors
import com.creatorcontenthub.infrastructure.http.pythonTimeouts
import java.net.HttpURLConnection
import java.net.URL
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

class PythonTextProcessorAdapter(
    private val endpoint: String
) : TextProcessingPort {

    private val logger = LoggerFactory.getLogger(PythonTextProcessorAdapter::class.java)

    // 🔹 Thread pool controlado (evita crescimento ilimitado)
    private val executor: ExecutorService = Executors.newFixedThreadPool(8)

    // 🔹 Timeout configurável
    private val timeoutMs: Long =
        System.getenv("PYTHON_TIMEOUT_MS")?.toLongOrNull() ?: 2000L

    // 🔹 Métricas básicas
    private val totalCalls = AtomicLong(0)
    private val totalErrors = AtomicLong(0)
    private val totalTimeouts = AtomicLong(0)

    override fun process(text: String): String {
        val start = System.currentTimeMillis()
        pythonCalls.incrementAndGet()

        val future = executor.submit<String> {
            val response = callPython(text)
            extractProcessedText(response)
        }

        try {
            val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)

            val duration = System.currentTimeMillis() - start
            logger.info("Python call took ${duration}ms")

            return result

        } catch (ex: TimeoutException) {
            val duration = System.currentTimeMillis() - start
            pythonTimeouts.incrementAndGet()

            future.cancel(true)

            logger.error("python timeout after ${duration}ms", ex)

            throw ExternalServiceException("Timeout calling Python service", ex)

        } catch (ex: ExternalServiceException) {
            val duration = System.currentTimeMillis() - start
            pythonErrors.incrementAndGet()

            logger.error("python http_error after ${duration}ms", ex)

            throw ex

        } catch (ex: Exception) {
            val duration = System.currentTimeMillis() - start
            pythonErrors.incrementAndGet()

            logger.error("python unexpected_error after ${duration}ms", ex)

            throw ExternalServiceException("Error calling Python service", ex)
        }
    }

    private fun callPython(text: String): String {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 3000
        connection.readTimeout = 5000
        connection.doOutput = true
        connection.setRequestProperty(
            "Content-Type",
            "application/json; charset=UTF-8"
        )

        val safeText = text.replace("\"", "\\\"")
        val body = """{"text":"$safeText"}"""

        connection.outputStream.use {
            it.write(body.toByteArray(Charsets.UTF_8))
        }

        val status = connection.responseCode

        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream
            .bufferedReader(Charsets.UTF_8)
            .readText()

        if (status !in 200..299) {
            throw ExternalServiceException(
                "Python service error: HTTP $status - $response"
            )
        }

        return response
    }

    private fun extractProcessedText(raw: String): String {
        return Regex("\"result\"\\s*:\\s*\"(.*?)\"")
            .find(raw)
            ?.groupValues?.get(1)
            ?: raw
    }
}