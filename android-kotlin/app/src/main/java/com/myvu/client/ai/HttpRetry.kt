package com.myvu.client.ai

import com.myvu.client.core.LogBus
import java.io.IOException

object HttpRetry {
    const val DEFAULT_MAX_ATTEMPTS: Int = 3
    const val INITIAL_BACKOFF_MS: Long = 1000L

    fun interface Request<T> {
        @Throws(IOException::class)
        fun execute(): T
    }

    class StatusException(val statusCode: Int, message: String) : IOException(message)

    @JvmStatic
    fun isRetryable(e: Exception): Boolean {
        if (e is StatusException) {
            val code = e.statusCode
            return code == 429 || code >= 500
        }
        return e is IOException
    }

    @JvmStatic
    fun statusError(statusCode: Int, message: String): StatusException {
        return StatusException(statusCode, message)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun <T> execute(label: String, request: Request<T>): T {
        return execute(label, DEFAULT_MAX_ATTEMPTS, INITIAL_BACKOFF_MS, request)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun <T> execute(
        label: String,
        maxAttempts: Int,
        initialBackoffMs: Long,
        request: Request<T>
    ): T {
        var attempt = 1
        var backoffMs = initialBackoffMs
        while (true) {
            try {
                return request.execute()
            } catch (e: IOException) {
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e
                }
                LogBus.log(
                    "$label request failed (attempt $attempt/$maxAttempts: ${e.message}) -- retrying in ${backoffMs}ms"
                )
                try {
                    Thread.sleep(backoffMs)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
                attempt++
                backoffMs *= 2
            }
        }
    }
}
