package com.myvu.client.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.myvu.client.core.LogBus
import java.util.ArrayList

/** Speech recognition via Android's built-in SpeechRecognizer. */
class SttSource(private val context: Context) {

    interface Listener {
        fun onSpeechStart()
        fun onPartial(text: String)
        fun onSpeechEnd()
        fun onFinal(text: String)
        fun onError(reason: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var speechStarted = false

    fun start(l: Listener) {
        this.listener = l
        this.speechStarted = false

        if (!isAvailable(context)) {
            l.onError("no speech recognition service on this device")
            return
        }

        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    LogBus.trace("stt: ready")
                }

                override fun onBeginningOfSpeech() {
                    speechStarted = true
                    listener?.onSpeechStart()
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listener?.onSpeechEnd()
                }

                override fun onError(error: Int) {
                    listener?.onError(describeError(error))
                }

                override fun onResults(results: Bundle?) {
                    listener?.onFinal(firstResult(results))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = firstResult(partialResults)
                    if (text.isNotEmpty()) listener?.onPartial(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CO")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

        recognizer?.startListening(intent)
    }

    fun stop() {
        if (recognizer != null) {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (ignored: Exception) {
            }
            recognizer = null
        }
    }

    fun speechDetected(): Boolean = speechStarted

    companion object {
        @JvmStatic
        fun isAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }

        private fun firstResult(bundle: Bundle?): String {
            if (bundle == null) return ""
            val list: ArrayList<String>? = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            return if (list.isNullOrEmpty()) "" else list[0]
        }

        private fun describeError(error: Int): String {
            return when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission not granted"
                SpeechRecognizer.ERROR_NETWORK -> "network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "nothing recognised"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recogniser busy"
                SpeechRecognizer.ERROR_SERVER -> "server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech heard"
                else -> "speech error $error"
            }
        }
    }
}
