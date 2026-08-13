package com.myvu.client.ai

import com.myvu.client.core.LogBus
import com.myvu.client.core.SslUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

/**
 * Shared plumbing for the provider clients: one JSON POST, one JSON answer.
 */
abstract class AiHttpClient @JvmOverloads constructor(
    protected val provider: AiProvider,
    protected val apiKey: String?,
    model: String?,
    systemPrompt: String?,
    protected val ignoreSsl: Boolean = false
) : AiClient {

    protected val model: String = if (model.isNullOrBlank()) provider.displayName else model.trim()
    protected val systemPrompt: String = if (systemPrompt.isNullOrBlank()) AiClient.DEFAULT_SYSTEM_PROMPT else systemPrompt.trim()

    override fun isConfigured(): Boolean {
        return !apiKey.isNullOrBlank()
    }

    protected abstract fun endpoint(): String
    protected abstract fun authorize(conn: HttpURLConnection)
    @Throws(JSONException::class)
    protected abstract fun buildBody(question: String): String
    @Throws(JSONException::class)
    protected abstract fun extractText(response: String): String

    @Throws(IOException::class)
    override fun ask(question: String): String {
        if (!isConfigured()) {
            throw IOException("${provider.displayName} is not fully configured")
        }

        val body = try {
            buildBody(question)
        } catch (e: JSONException) {
            throw IOException("could not build the request: ${e.message}", e)
        }

        return HttpRetry.execute(provider.displayName) {
            askOnce(body)
        }
    }

    @Throws(IOException::class)
    private fun askOnce(body: String): String {
        return askOnceInternal(body, ignoreSsl)
    }

    @Throws(IOException::class)
    private fun askOnceInternal(body: String, bypassSsl: Boolean): String {
        val url = HttpEndpoint.parse(endpoint(), "${provider.displayName} endpoint")
        val conn = url.openConnection() as HttpURLConnection
        if (bypassSsl) {
            SslUtils.applySslBypass(conn)
        }
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("content-type", "application/json")
            authorize(conn)
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true

            conn.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val response = readAll(stream)
            if (status >= 400) {
                throw HttpRetry.statusError(
                    status,
                    "${provider.displayName} API returned $status: ${extractError(response)}"
                )
            }

            val text = try {
                extractText(response).trim()
            } catch (e: JSONException) {
                throw IOException("unparseable ${provider.displayName} response: ${e.message}", e)
            }
            if (text.isEmpty()) {
                throw IOException("${provider.displayName} returned an empty answer")
            }
            return text
        } catch (e: SSLException) {
            if (!bypassSsl) {
                LogBus.warn("${provider.displayName} SSL failed, retrying with SSL bypass...")
                return askOnceInternal(body, true)
            }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 30000

        private fun extractError(response: String): String {
            try {
                val error = JSONObject(response).optJSONObject("error")
                if (error != null) return error.optString("message", response)
            } catch (ignored: JSONException) {
            }
            return response.substring(0, Math.min(200, response.length))
        }

        private fun readAll(input: InputStream?): String {
            if (input == null) return ""
            return input.use { String(it.readBytes(), StandardCharsets.UTF_8) }
        }
    }
}
