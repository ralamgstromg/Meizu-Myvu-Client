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
    @JvmOverloads
    protected fun postRaw(body: String, customReadTimeoutMs: Int? = null): String {
        return postRawInternal(body, ignoreSsl, customReadTimeoutMs)
    }

    @Throws(IOException::class)
    private fun postRawInternal(body: String, bypassSsl: Boolean, customReadTimeoutMs: Int? = null): String {
        val targetUrl = endpoint()
        LogBus.log("${provider.displayName}: POST $targetUrl (payload: ${body.length} chars)")
        val url = HttpEndpoint.parse(targetUrl, "${provider.displayName} endpoint")
        val conn = url.openConnection() as HttpURLConnection
        if (bypassSsl) {
            SslUtils.applySslBypass(conn)
        }
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("content-type", "application/json")
            authorize(conn)
            val isPrivateHost = targetUrl.contains("://10.") ||
                    targetUrl.contains("://192.168.") ||
                    targetUrl.contains("://172.16.") ||
                    targetUrl.contains("://172.17.") ||
                    targetUrl.contains("://172.18.") ||
                    targetUrl.contains("://172.19.") ||
                    targetUrl.contains("://172.2") ||
                    targetUrl.contains("://172.30.") ||
                    targetUrl.contains("://172.31.") ||
                    targetUrl.contains("localhost") ||
                    targetUrl.contains("127.0.0.1")
            val isLocal = isPrivateHost
            conn.connectTimeout = if (isLocal) LOCAL_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
            conn.readTimeout = customReadTimeoutMs ?: if (isLocal) LOCAL_READ_TIMEOUT_MS else READ_TIMEOUT_MS
            conn.doOutput = true

            conn.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val response = readAll(stream)
            LogBus.log("${provider.displayName}: HTTP $status (${response.length} chars received)")
            if (status >= 400) {
                throw HttpRetry.statusError(
                    status,
                    "${provider.displayName} API returned $status: ${extractError(response)}"
                )
            }
            return response
        } catch (e: SSLException) {
            if (!bypassSsl) {
                LogBus.warn("${provider.displayName} SSL failed, retrying with SSL bypass...")
                return postRawInternal(body, true, customReadTimeoutMs)
            }
            throw e
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    protected fun askOnce(body: String): String {
        val response = postRaw(body)
        val text = try {
            extractText(response).trim()
        } catch (e: JSONException) {
            throw IOException("unparseable ${provider.displayName} response: ${e.message}", e)
        }
        if (text.isEmpty()) {
            throw IOException("${provider.displayName} returned an empty answer")
        }
        return text
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 60000
        private const val LOCAL_CONNECT_TIMEOUT_MS = 15000
        private const val LOCAL_READ_TIMEOUT_MS = 30000

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
