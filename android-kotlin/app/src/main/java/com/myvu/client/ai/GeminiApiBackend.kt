package com.myvu.client.ai

import com.myvu.client.core.LogBus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Gemini generateContent backend with bounded, injectable HTTP transport. */
class GeminiApiBackend(
    private val apiKey: String,
    private val model: String,
    private val transport: GeminiApiTransport = UrlConnectionGeminiApiTransport()
) : GeminiBackend {
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val activeRequests = ConcurrentHashMap<String, Unit>()

    internal fun activeRequestCountForTest(): Int = activeRequests.size
    internal fun cancelledRequestCountForTest(): Int = cancelled.size

    override fun availability(): GeminiAvailability {
        return if (apiKey.isBlank() || model.isBlank()) {
            GeminiAvailability(GeminiAvailability.State.UNAVAILABLE, "missing_configuration")
        } else {
            GeminiAvailability(GeminiAvailability.State.AVAILABLE)
        }
    }

    override fun ask(request: GeminiRequest, callback: (Result<GeminiResult>) -> Unit) {
        val requestId = request.requestId
        if (request.requestId.isBlank()) {
            callback(Result.failure(GeminiApiException(GeminiApiException.Kind.CONFIGURATION, "missing_request_id")))
            return
        }
        if (apiKey.isBlank() || model.isBlank()) {
            callback(Result.failure(GeminiApiException(GeminiApiException.Kind.CONFIGURATION, "missing_configuration")))
            return
        }
        synchronized(activeRequests) {
            cancelled.remove(requestId)
            activeRequests[requestId] = Unit
            if (cancelled.contains(requestId)) {
                activeRequests.remove(requestId)
                cancelled.remove(requestId)
                return
            }
        }
        thread(name = "gemini-api-$requestId") {
            if (isCancelled(requestId)) {
                finishRequest(requestId)
                return@thread
            }
            val result = runCatching {
                val body = buildRequestBody(request)
                if (isCancelled(requestId)) return@runCatching null
                val response = transport.post(requestId, endpoint(model), apiKey, body)
                if (isCancelled(requestId)) return@runCatching null
                if (response.statusCode !in 200..299) {
                    val kind = if (response.statusCode == 401 || response.statusCode == 403) {
                        GeminiApiException.Kind.CONFIGURATION
                    } else {
                        GeminiApiException.Kind.HTTP
                    }
                    throw GeminiApiException(kind, "http_${response.statusCode}")
                }
                parseResult(request.requestId, response.body)
            }.fold(
                onSuccess = { value -> value?.let { Result.success(it) } },
                onFailure = { error -> Result.failure(classify(error)) }
            )
            if (!isCancelled(requestId) && result != null) callback(result)
            finishRequest(requestId)
        }
    }

    override fun cancel(requestId: String) {
        synchronized(activeRequests) {
            if (!activeRequests.containsKey(requestId)) return
            cancelled += requestId
        }
        transport.cancel(requestId)
    }

    private fun isCancelled(requestId: String): Boolean = synchronized(activeRequests) {
        cancelled.contains(requestId)
    }

    private fun finishRequest(requestId: String) {
        synchronized(activeRequests) {
            activeRequests.remove(requestId)
            cancelled.remove(requestId)
        }
    }

    private fun classify(error: Throwable): GeminiApiException {
        return when (error) {
            is GeminiApiException -> error
            is JSONException -> GeminiApiException(GeminiApiException.Kind.MALFORMED_RESPONSE, "malformed_response")
            is IOException -> GeminiApiException(GeminiApiException.Kind.NETWORK, "network_failure")
            else -> GeminiApiException(GeminiApiException.Kind.NETWORK, "request_failure")
        }
    }

    private fun buildRequestBody(request: GeminiRequest): String {
        val config = JSONObject()
            .put("maxOutputTokens", MAX_OUTPUT_TOKENS)
        if (request.requireStructuredOutput) {
            config.put("responseMimeType", "application/json")
        }
        return JSONObject()
            .put("systemInstruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", request.systemInstruction))))
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", request.prompt)))))
            .put("generationConfig", config)
            .toString()
    }

    private fun parseResult(requestId: String, body: String): GeminiResult {
        val parts = JSONObject(body)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        val answer = buildString {
            for (index in 0 until parts.length()) append(parts.getJSONObject(index).optString("text"))
        }.trim()
        if (answer.isEmpty()) throw JSONException("empty answer")
        return GeminiResult(requestId, answer, BACKEND_ID)
    }

    companion object {
        const val BACKEND_ID = "gemini_api"
        private const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MAX_OUTPUT_TOKENS = 4096
        private fun endpoint(model: String): String = "$BASE_ENDPOINT${model.trim()}:generateContent"
    }
}

data class GeminiHttpResponse(val statusCode: Int, val body: String)

interface GeminiApiTransport {
    fun post(requestId: String, url: String, apiKey: String, requestBody: String): GeminiHttpResponse
    fun cancel(requestId: String) = Unit
}

class GeminiApiException(
    val kind: Kind,
    message: String
) : IOException(message) {
    enum class Kind { CONFIGURATION, HTTP, NETWORK, MALFORMED_RESPONSE }
}

private class UrlConnectionGeminiApiTransport : GeminiApiTransport {
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()

    override fun post(requestId: String, url: String, apiKey: String, requestBody: String): GeminiHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        connections[requestId] = connection
        if (Thread.currentThread().isInterrupted) {
            connection.disconnect()
            throw IOException("cancelled")
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(requestBody.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val response = stream?.use { readGeminiResponseBounded(it) } ?: ""
            LogBus.log("AI_GEMINI_API_HTTP status=$status responseLength=${response.length}")
            return GeminiHttpResponse(status, response)
        } finally {
            connections.remove(requestId)
            connection.disconnect()
        }
    }

    override fun cancel(requestId: String) {
        connections.remove(requestId)?.disconnect()
    }

    companion object {
        private const val TIMEOUT_MS = 30_000
    }
}

internal const val GEMINI_MAX_RESPONSE_BYTES = 512 * 1024

internal fun readGeminiResponseBounded(input: java.io.InputStream): String {
    val buffer = ByteArray(8192)
    val output = java.io.ByteArrayOutputStream(8192)
    var total = 0
    while (true) {
        val remaining = GEMINI_MAX_RESPONSE_BYTES + 1 - total
        if (remaining <= 0) {
            throw GeminiApiException(GeminiApiException.Kind.MALFORMED_RESPONSE, "response_too_large")
        }
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        total += count
        if (total > GEMINI_MAX_RESPONSE_BYTES) {
            throw GeminiApiException(GeminiApiException.Kind.MALFORMED_RESPONSE, "response_too_large")
        }
        output.write(buffer, 0, count)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}
