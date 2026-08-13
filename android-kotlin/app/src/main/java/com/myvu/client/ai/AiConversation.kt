package com.myvu.client.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.myvu.client.app.feature.AiProtocol
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import com.myvu.client.database.ReminderRepository
import com.myvu.client.service.ConnectionState
import com.myvu.client.service.MyvuService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The AI assistant, driven by the GLASSES' microphone.
 */
class AiConversation(
    context: Context,
    private val sender: Sender
) {

    fun interface Sender {
        fun send(actionJson: String, targetPkg: String, sourcePkg: String)
    }

    private val context: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ai-worker").apply { isDaemon = true }
    }
    private val mic = GlassesMicStream()
    private val decoder = OpusDecoderStream()
    private val tts = TtsPlayer(this.context)
    private val actionExecutor = PhoneActionExecutor(this.context)
    private val audio: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ai-audio").apply { isDaemon = true }
    }

    @Volatile
    private var lastSpeechAt: Long = 0
    @Volatile
    private var decoding: Boolean = false
    @Volatile
    private var peakEnergy: Double = 0.0
    @Volatile
    private var decodedBytes: Int = 0
    @Volatile
    private var noiseFloor: Double = 0.0
    @Volatile
    private var noiseChunks: Int = 0
    @Volatile
    private var loudStreak: Int = 0
    @Volatile
    private var speechThreshold: Double = SPEECH_ENERGY

    @Volatile
    private var active: Boolean = false
    private var sessionId: String = ""
    private var turnCount: Int = 0
    private var stopRequested: Boolean = false
    private var speechStarted: Boolean = false
    private var lastTriggerAt: Long = 0
    private var textMode: Boolean = false

    private val silenceTimeout = Runnable { endUtterance() }
    private val utteranceCap = Runnable {
        LogBus.log("AI: utterance hit the length cap")
        endUtterance()
    }

    fun isActive(): Boolean = active

    fun onTrigger(triggerCode: Int) {
        main.post {
            val now = System.currentTimeMillis()
            if (now - lastTriggerAt < DUPLICATE_TRIGGER_MS) {
                LogBus.trace("AI trigger ignored -- duplicate of the last press")
                return@post
            }
            lastTriggerAt = now

            if (active) {
                LogBus.log("AI: new press while a turn was open -- restarting")
                abandon()
            }
            begin(triggerCode)
        }
    }

    fun onPageClosed() {
        main.post {
            if (!active) return@post
            stopRequested = true
            LogBus.trace("AI page closed -- will end after this turn")
        }
    }

    fun onAudioFrame(relayBody: ByteArray): Boolean {
        if (!mic.offer(relayBody)) return false
        if (!mic.isCapturing()) return true

        val frames = ArrayList(mic.justAddedFrames())
        if (frames.isEmpty()) return true

        for (f in frames) {
            f.retain()
        }

        audio.execute {
            try {
                for (frame in frames) {
                    if (!decoding) return@execute
                    decoder.feed(frame.buffer(), frame.length) { pcmChunk, length ->
                        decodedBytes += length
                        val level = OpusDecoderStream.energy(pcmChunk)
                        if (level > peakEnergy) peakEnergy = level
                        consume(level)
                    }
                }
            } finally {
                for (f in frames) {
                    f.release()
                }
            }
        }
        return true
    }

    private fun consume(level: Double) {
        if (noiseChunks < NOISE_CALIBRATION_CHUNKS) {
            if (level < speechThreshold) {
                loudStreak = 0
                noiseChunks++
                noiseFloor = ((noiseFloor * (noiseChunks - 1)) + level) / noiseChunks
                speechThreshold = Math.max(SPEECH_ENERGY, noiseFloor * SPEECH_OVER_NOISE)
                if (noiseChunks == NOISE_CALIBRATION_CHUNKS) {
                    LogBus.trace(
                        String.format(
                            Locale.US,
                            "AI: noise floor %.0f, speech threshold %.0f",
                            noiseFloor, speechThreshold
                        )
                    )
                }
                return
            }
            if (++loudStreak < CALIBRATION_LOUD_STREAK) return
            noiseChunks = NOISE_CALIBRATION_CHUNKS
            LogBus.trace(
                String.format(
                    Locale.US,
                    "AI: speech before calibration finished -- floor %.0f, threshold %.0f",
                    noiseFloor, speechThreshold
                )
            )
        }

        if (level >= speechThreshold) {
            lastSpeechAt = System.currentTimeMillis()
            if (!speechStarted) {
                speechStarted = true
                main.post {
                    if (!active) return@post
                    main.removeCallbacks(silenceTimeout)
                    send(AiProtocol.vadStart(sessionId))
                    LogBus.log("AI: speech detected")
                }
            }
        } else {
            if (!speechStarted && noiseChunks >= NOISE_CALIBRATION_CHUNKS) {
                noiseFloor = (noiseFloor * 0.95) + (level * 0.05)
                speechThreshold = Math.max(SPEECH_ENERGY, noiseFloor * SPEECH_OVER_NOISE)
            }
            if (speechStarted && System.currentTimeMillis() - lastSpeechAt > SILENCE_HOLD_MS) {
                decoding = false
                main.post { endUtterance() }
            }
        }
    }

    private fun begin(triggerCode: Int) {
        active = true
        stopRequested = false
        textMode = false
        turnCount = 0
        send(AiProtocol.assistantConfig(Prefs.voiceWakeupEnabled(context)))
        prepareTts()
        startListening(if (triggerCode == AiProtocol.CODE_START_VR_REQ) "button" else "wake word")
    }

    private fun startListening(why: String) {
        sessionId = UUID.randomUUID().toString()
        speechStarted = false
        lastSpeechAt = 0
        peakEnergy = 0.0
        decodedBytes = 0
        noiseFloor = 0.0
        noiseChunks = 0
        loudStreak = 0
        speechThreshold = SPEECH_ENERGY
        try {
            decoder.start()
            decoding = true
        } catch (e: Exception) {
            LogBus.error("could not start the Opus decoder", e)
            finish()
            return
        }
        mic.start()

        send(AiProtocol.sessionAck(sessionId))
        LogBus.log("AI listening ($why)")

        main.removeCallbacks(silenceTimeout)
        main.postDelayed(silenceTimeout, NO_SPEECH_TIMEOUT_MS)
        main.removeCallbacks(utteranceCap)
        main.postDelayed(utteranceCap, MAX_UTTERANCE_MS)
    }

    private fun endUtterance() {
        if (!active || !mic.isCapturing()) return
        main.removeCallbacks(silenceTimeout)
        main.removeCallbacks(utteranceCap)
        mic.stop()
        decoding = false

        if (!speechStarted) {
            LogBus.warn(
                String.format(
                    Locale.US,
                    "AI: no speech heard -- %d packets in (%d unreadable), sizes %s, " +
                            "%d bytes decoded (%dms), peak energy %.0f vs threshold %.0f",
                    mic.packetCount(), mic.rejectedCount(), mic.observedSizes(),
                    decodedBytes,
                    decodedBytes / 2 * 1000 / Math.max(1, decoder.sampleRate()),
                    peakEnergy, speechThreshold
                )
            )
            finish()
            return
        }
        send(AiProtocol.vadEnd(sessionId))

        decoder.finish()
        val pcm = decoder.allPcm()
        decoder.stop()
        LogBus.log(
            "captured ${pcm.size / 2} samples (${pcm.size / 2 * 1000 / Math.max(1, decoder.sampleRate())}ms @ ${decoder.sampleRate()}Hz) from ${mic.packetCount()} Opus packets"
        )
        if (pcm.isEmpty()) {
            LogBus.log("AI: no audio decoded -- ending")
            finish()
            return
        }
        transcribe(pcm, decoder.sampleRate(), decoder.channels())
    }

    private fun transcribe(pcm: ByteArray, sampleRate: Int, channels: Int) {
        val sttProviderId = Prefs.sttProvider(context)
        val apiKey = Prefs.sttApiKey(context, sttProviderId)
        val storedModel = Prefs.sttModel(context, sttProviderId).trim()
        val model = if (storedModel.isEmpty()) "whisper-1" else storedModel
        val storedEndpoint = Prefs.sttEndpoint(context, sttProviderId).trim()
        val endpoint = if (storedEndpoint.isEmpty()) "https://api.openai.com/v1/audio/transcriptions" else storedEndpoint
        val client = OpenAiTranscriptionClient(
            endpoint, model, apiKey, sttProviderId, Prefs.ignoreSsl(context)
        )
        if (!client.isConfigured()) {
            LogBus.warn("$sttProviderId is not fully configured")
            finish()
            return
        }
        worker.execute {
            val text = try {
                client.transcribe(pcm, sampleRate, channels)
            } catch (e: Exception) {
                LogBus.error("could not transcribe the glasses audio", e)
                main.post { finish() }
                return@execute
            }
            main.post { onTranscript(text) }
        }
    }

    private fun onTranscript(text: String?) {
        if (!active) return
        if (text.isNullOrBlank()) {
            LogBus.log("AI: nothing understood -- ending the conversation")
            finish()
            return
        }
        if (isStopPhrase(text)) {
            LogBus.log("AI: stop phrase heard (\"${text.trim()}\")")
            finish()
            return
        }
        LogBus.log("AI heard: $text")
        sendGrowingCaption(text.trim(), 0)
    }

    private fun sendGrowingCaption(text: String, wordIndex: Int) {
        if (!active) return
        val words = text.split(Regex("\\s+"))

        if (wordIndex >= words.size) {
            send(AiProtocol.asrResult(sessionId, text, true))
            send(AiProtocol.vrState(AiProtocol.VR_PROCESSION))
            send(AiProtocol.chatQuery(sessionId, text))
            askAi(text)
            return
        }

        val partial = StringBuilder()
        for (i in 0..wordIndex) {
            if (i > 0) partial.append(' ')
            partial.append(words[i])
        }
        send(AiProtocol.asrResult(sessionId, partial.toString(), false))

        main.postDelayed({
            sendGrowingCaption(text, wordIndex + 1)
        }, CAPTION_WORD_MS)
    }

    private fun buildContextPayload(): String {
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "CO"))
        val currentDateTime = sdf.format(Date()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
        val conn = MyvuService.activeConnection()
        val batteryInfo = if (conn?.state == ConnectionState.READY) {
            val batt = conn.glassesInfo()?.battery?.takeIf { it > 0 } ?: 85
            "Gafas AR MYVU Conectadas (Batería: $batt%)"
        } else {
            "Gafas AR Desconectadas"
        }

        val reminderRepo = ReminderRepository(context)
        val upcoming = reminderRepo.getPendingReminders()
            .filter { it.triggerAt > System.currentTimeMillis() }
            .take(2)
            .joinToString("; ") {
                val text = it.title.ifBlank { it.body }
                "$text a las ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.triggerAt))}"
            }

        return "[Contexto del Sistema: $currentDateTime | $batteryInfo | Próximos recordatorios: ${upcoming.ifEmpty { "Ninguno" }}]\n"
    }

    private fun askAi(question: String) {
        val aiProviderId = Prefs.aiProvider(context)
        val provider = AiProvider.fromId(aiProviderId)
        val apiKey = Prefs.aiApiKey(context, aiProviderId)
        val model = Prefs.aiModel(context, aiProviderId)
        val endpoint = Prefs.aiEndpoint(context, aiProviderId)
        val prompt = Prefs.systemPrompt(context)
        val client = provider.newClient(context, apiKey, model, endpoint, prompt)
        if (!client.isConfigured()) {
            LogBus.warn("$aiProviderId is not fully configured -- check Settings")
            finish()
            return
        }
        worker.execute {
            val contextPayload = buildContextPayload()
            val fullPrompt = contextPayload + question
            LogBus.log("AI prompt with context: $fullPrompt")
            val answer: String?
            try {
                answer = client.ask(fullPrompt)
            } catch (e: Exception) {
                LogBus.error("$aiProviderId request failed", e)
                main.post { finish() }
                return@execute
            }
            main.post { deliver(answer) }
        }
    }

    private fun deliver(rawAnswer: String?) {
        if (!active) return
        var answer = actionExecutor.processAndExecute(rawAnswer)
        if (answer.isBlank()) {
            answer = "Acción ejecutada en el teléfono."
        }
        LogBus.log("AI answer: $answer")

        send(AiProtocol.chatAnswer(sessionId, answer, 1))
        send(AiProtocol.chatAnswer(sessionId, answer, 2))
        send(AiProtocol.playState(AiProtocol.PLAY_STATE_START))

        val callback = TtsPlayer.Callback { success ->
            send(AiProtocol.playState(AiProtocol.PLAY_STATE_END))
            send(AiProtocol.endTurn())
            if (!success) LogBus.warn("the answer could not be spoken aloud")
            if (textMode || !SPOKEN_FOLLOW_UP_TURNS) finish() else nextTurn()
        }

        tts.speak(answer, callback)
    }

    fun askText(question: String?) {
        if (question.isNullOrBlank()) return
        main.post {
            if (active) abandon()
            active = true
            stopRequested = false
            textMode = true
            turnCount = 0
            send(AiProtocol.assistantConfig(Prefs.voiceWakeupEnabled(context)))
            prepareTts()
            sessionId = UUID.randomUUID().toString()

            send(AiProtocol.sessionAck(sessionId))
            send(AiProtocol.asrResult(sessionId, question.trim(), true))
            send(AiProtocol.vrState(AiProtocol.VR_PROCESSION))
            send(AiProtocol.chatQuery(sessionId, question.trim()))
            LogBus.log("AI (typed): ${question.trim()}")
            askAi(question.trim())
        }
    }

    private fun nextTurn() {
        if (!active) return
        if (stopRequested) {
            LogBus.log("AI: page was closed -- ending the conversation")
            finish()
            return
        }
        if (++turnCount >= MAX_TURNS) {
            LogBus.log("AI: conversation limit reached")
            finish()
            return
        }

        startListening("follow-up ${turnCount + 1}")
    }

    private fun finish() {
        if (!active) return
        active = false
        stopRequested = false
        mic.stop()
        decoding = false
        decoder.stop()
        main.removeCallbacks(silenceTimeout)
        main.removeCallbacks(utteranceCap)
        send(AiProtocol.vrState(AiProtocol.VR_CLOSE))
        LogBus.trace("AI conversation ended")
    }

    private fun abandon() {
        active = false
        stopRequested = false
        mic.stop()
        decoding = false
        decoder.stop()
        main.removeCallbacks(silenceTimeout)
        main.removeCallbacks(utteranceCap)
    }

    fun stop() {
        main.post { finish() }
    }

    fun shutdown() {
        stop()
        main.post { tts.shutdown() }
        worker.shutdownNow()
        audio.shutdownNow()
    }

    private fun prepareTts() {
        tts.init()
    }

    private fun send(actionJson: String?) {
        if (actionJson == null) return
        sender.send(actionJson, AiProtocol.PKG, AiProtocol.PKG)
    }

    companion object {
        private const val SILENCE_HOLD_MS = 450L
        private const val SPEECH_ENERGY = 75.0
        private const val SPEECH_OVER_NOISE = 2.8
        private const val NOISE_CALIBRATION_CHUNKS = 12
        private const val CALIBRATION_LOUD_STREAK = 3
        private const val NO_SPEECH_TIMEOUT_MS = 5000L
        private const val MAX_UTTERANCE_MS = 20000L
        private const val CAPTION_WORD_MS = 180L
        private const val DUPLICATE_TRIGGER_MS = 1500L
        private const val MAX_TURNS = 2
        private const val SPOKEN_FOLLOW_UP_TURNS = false

        private val STOP_PHRASES = arrayOf(
            "stop", "goodbye", "good bye", "bye", "exit", "quit",
            "that's all", "thats all", "that is all", "never mind", "nevermind",
            "thank you", "thanks", "cancel", "end"
        )

        private fun isStopPhrase(text: String): Boolean {
            val t = text.trim().lowercase(Locale.US).replace(Regex("[.!?,]"), "")
            for (phrase in STOP_PHRASES) {
                if (t == phrase) return true
            }
            return false
        }
    }
}
