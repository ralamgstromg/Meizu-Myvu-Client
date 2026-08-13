package com.myvu.client.app.feature

import java.util.ArrayDeque
import java.util.Queue

/**
 * Splits and queues long text responses into readable sentence-based HUD frames.
 */
class HudMessageQueue {
    companion object {
        private const val MAX_HUD_FRAME_LEN = 80

        private fun splitIntoSentences(text: String): List<String> {
            val result = ArrayList<String>()
            val parts = text.split(Regex("(?<=[\\.\\?\\!\\n])\\s+"))
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    result.add(trimmed)
                }
            }
            return result
        }
    }

    private val pendingFrames: Queue<String> = ArrayDeque()

    fun enqueue(fullText: String?) {
        pendingFrames.clear()
        if (fullText.isNullOrBlank()) return

        val sentences = splitIntoSentences(fullText.trim())
        for (sentence in sentences) {
            if (sentence.length <= MAX_HUD_FRAME_LEN) {
                pendingFrames.add(sentence)
            } else {
                val words = sentence.split(Regex("\\s+"))
                val sb = StringBuilder()
                for (word in words) {
                    if (sb.length + word.length + 1 > MAX_HUD_FRAME_LEN) {
                        pendingFrames.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(word)
                }
                if (sb.isNotEmpty()) {
                    pendingFrames.add(sb.toString().trim())
                }
            }
        }
    }

    fun hasNext(): Boolean = !pendingFrames.isEmpty()

    fun pollNext(): String? = pendingFrames.poll()

    fun clear() {
        pendingFrames.clear()
    }
}
