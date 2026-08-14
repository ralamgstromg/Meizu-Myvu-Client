package com.myvu.client.ai

import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GeminiHybridClient(
    private val nanoBackend: GeminiBackend,
    private val apiBackend: GeminiBackend,
    private val policy: GeminiFallbackPolicy = GeminiFallbackPolicy.NANO_THEN_API,
    private val systemPrompt: String = AiClient.DEFAULT_SYSTEM_PROMPT
) : AiClient {

    override fun isConfigured(): Boolean = true

    @Throws(IOException::class)
    override fun ask(question: String): String {
        val requestId = UUID.randomUUID().toString()
        val request = GeminiRequest(
            requestId = requestId,
            prompt = question,
            systemInstruction = systemPrompt
        )

        val latch = CountDownLatch(1)
        var finalResult: Result<GeminiResult>? = null

        val executeApi = {
            apiBackend.ask(request) { res ->
                finalResult = res
                latch.countDown()
            }
        }

        val executeNano = {
            nanoBackend.ask(request) { res ->
                res.fold(
                    onSuccess = { nanoRes ->
                        finalResult = Result.success(nanoRes)
                        latch.countDown()
                    },
                    onFailure = { err ->
                        val isEligible = isEligibleForFallback(err)
                        if (policy.allowsApiFallback && isEligible) {
                            executeApi()
                        } else {
                            finalResult = Result.failure(
                                if (err is IOException) err else IOException("Nano failed: ${err.message}", err)
                            )
                            latch.countDown()
                        }
                    }
                )
            }
        }

        when (policy) {
            GeminiFallbackPolicy.API_ONLY -> executeApi()
            GeminiFallbackPolicy.NANO_ONLY, GeminiFallbackPolicy.NANO_THEN_API -> executeNano()
        }

        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                when (policy) {
                    GeminiFallbackPolicy.API_ONLY -> apiBackend.cancel(requestId)
                    else -> {
                        nanoBackend.cancel(requestId)
                        apiBackend.cancel(requestId)
                    }
                }
                throw IOException("Gemini Hybrid request timed out")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted during Gemini request", e)
        }

        val res = finalResult ?: throw IOException("Gemini Hybrid request produced no result")
        return res.getOrElse { err ->
            if (err is IOException) throw err else throw IOException(err.message, err)
        }.answer
    }

    private fun isEligibleForFallback(err: Throwable): Boolean {
        if (err is GeminiNanoException) {
            return when (err.state) {
                GeminiAvailability.State.MODEL_MISSING,
                GeminiAvailability.State.UNAVAILABLE,
                GeminiAvailability.State.TASK_UNSUPPORTED -> true
                GeminiAvailability.State.AVAILABLE -> false
            }
        }
        return true
    }
}
