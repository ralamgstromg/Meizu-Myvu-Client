package com.myvu.client.ai

import com.myvu.client.core.SslUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

/** Downloads WAV speech from an OpenAI-compatible audio-speech endpoint. */
class HttpTtsClient(
    endpoint: String?,
    apiKey: String?,
    model: String?,
    voice: String?
) {
    private val endpoint: String = endpoint?.trim() ?: ""
    private val apiKey: String = apiKey?.trim() ?: ""
    private val model: String = model?.trim() ?: ""
    private val voice: String = voice?.trim() ?: ""

    @Throws(IOException::class)
    fun synthesize(text: String): ByteArray {
        val body = buildBody(text)
        return HttpRetry.execute("TTS") {
            synthesizeOnce(body)
        }
    }

    @Throws(IOException::class)
    private fun synthesizeOnce(body: String): ByteArray {
        val url = HttpEndpoint.parse(endpoint, "TTS endpoint")
        val conn = url.openConnection() as HttpURLConnection
        SslUtils.applySslBypass(conn)
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("content-type", "application/json")
            if (apiKey.isNotEmpty()) {
                conn.setRequestProperty("authorization", "Bearer $apiKey")
            }
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true

            conn.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val status = conn.responseCode
            if (status >= 400) {
                val error = readAll(conn.errorStream, 8192)
                throw HttpRetry.statusError(
                    status,
                    "TTS API returned $status: ${error.substring(0, Math.min(500, error.length))}"
                )
            }
            val audio = readBytes(conn.inputStream, MAX_AUDIO_BYTES)
            if (audio.isEmpty()) throw IOException("TTS API returned empty audio")
            return audio
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun buildBody(text: String): String {
        try {
            val body = JSONObject()
                .put("input", text)
                .put("response_format", "wav")
            if (model.isNotEmpty()) body.put("model", model)
            if (voice.isNotEmpty()) body.put("voice", voice)
            return body.toString()
        } catch (e: JSONException) {
            throw IOException("could not build the TTS request: ${e.message}", e)
        }
    }

    companion object {
        private const val TIMEOUT_MS = 60000
        private const val MAX_AUDIO_BYTES = 25 * 1024 * 1024

        @Throws(IOException::class)
        private fun readAll(input: InputStream?, maxBytes: Int): String {
            return String(readBytes(input, maxBytes), StandardCharsets.UTF_8)
        }

        @Throws(IOException::class)
        private fun readBytes(input: InputStream?, maxBytes: Int): ByteArray {
            if (input == null) return ByteArray(0)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count: Int
            while (input.read(buffer).also { count = it } > 0) {
                if (out.size() + count > maxBytes) {
                    throw IOException("HTTP response exceeded $maxBytes bytes")
                }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }
}
