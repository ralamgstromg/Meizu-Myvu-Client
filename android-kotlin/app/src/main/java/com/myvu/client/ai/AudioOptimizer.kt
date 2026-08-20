package com.myvu.client.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-quality audio DSP pipeline for preparing speech audio before sending to STT services (Whisper).
 *
 * Implements:
 * 1. 5-tap anti-aliasing FIR filter & 3:1 decimation (48kHz -> 16kHz).
 * 2. High-pass IIR filter (80Hz cutoff) to strip low-frequency DC bias and ambient rumble.
 * 3. Silence trimming (noise gate) to prevent Whisper hallucinations on dead audio headers/trailers.
 * 4. Adaptive Soft-Knee Peak Normalization (targeting ~85% FS / 28000 peak) without clipping.
 */
object AudioOptimizer {

    // 5-tap anti-aliasing FIR filter coefficients normalized to sum to 1.0
    private val FIR_COEFFS = floatArrayOf(0.08f, 0.24f, 0.36f, 0.24f, 0.08f)

    private const val TARGET_PEAK_AMPLITUDE = 28000.0
    private const val MAX_GAIN = 4.0
    private const val SILENCE_THRESHOLD_SHORT = 350

    /**
     * Resamples 48kHz 16-bit PCM mono audio to 16kHz with anti-aliasing FIR filtering,
     * high-pass filtering, silence trimming, and peak normalization.
     */
    @JvmStatic
    fun optimize48kTo16k(pcm48k: ByteArray): ByteArray {
        if (pcm48k.size < 6) return pcm48k

        val inSamples = pcm48k.size / 2
        val inputShorts = ShortArray(inSamples)
        for (i in 0 until inSamples) {
            val low = pcm48k[i * 2].toInt() and 0xFF
            val high = pcm48k[i * 2 + 1].toInt() shl 8
            inputShorts[i] = (low or high).toShort()
        }

        val outSamples = inSamples / 3
        if (outSamples <= 0) return pcm48k
        val filtered16k = ShortArray(outSamples)

        // 1. Anti-aliasing FIR low-pass filter + 3:1 decimation
        var maxPeak = 0
        for (outIdx in 0 until outSamples) {
            val centerIn = outIdx * 3
            var filteredSample = 0.0f
            for (tap in -2..2) {
                val sampleIdx = (centerIn + tap).coerceIn(0, inSamples - 1)
                filteredSample += inputShorts[sampleIdx] * FIR_COEFFS[tap + 2]
            }
            val sampleShort = filteredSample.toInt().coerceIn(-32768, 32767).toShort()
            filtered16k[outIdx] = sampleShort
            val absVal = abs(sampleShort.toInt())
            if (absVal > maxPeak) maxPeak = absVal
        }

        // 2. High-pass filter (80Hz cutoff at 16kHz)
        val alpha = 0.9688 // ~80Hz high-pass alpha for 16kHz sample rate
        var prevIn = 0.0
        var prevOut = 0.0
        for (i in 0 until outSamples) {
            val x = filtered16k[i].toDouble()
            val y = alpha * (prevOut + x - prevIn)
            prevIn = x
            prevOut = y
            filtered16k[i] = y.coerceIn(-32768.0, 32767.0).toInt().toShort()
        }

        // 3. Trim leading and trailing dead silence
        val (startIndex, endIndex) = findSpeechBounds(filtered16k)
        val trimmedLength = max(0, endIndex - startIndex + 1)
        val activeShorts = if (trimmedLength > 0 && trimmedLength < outSamples) {
            ShortArray(trimmedLength) { i -> filtered16k[startIndex + i] }
        } else {
            filtered16k
        }

        // 4. Adaptive Soft-Knee Peak Normalization
        var activeMaxPeak = 0
        for (s in activeShorts) {
            val absV = abs(s.toInt())
            if (absV > activeMaxPeak) activeMaxPeak = absV
        }

        val gain = if (activeMaxPeak in 800..26000) {
            min(MAX_GAIN, TARGET_PEAK_AMPLITUDE / activeMaxPeak)
        } else {
            1.0
        }

        val outPcm = ByteArray(activeShorts.size * 2)
        for (i in activeShorts.indices) {
            val scaled = (activeShorts[i] * gain).toInt().coerceIn(-32768, 32767)
            outPcm[i * 2] = (scaled and 0xFF).toByte()
            outPcm[i * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()
        }

        return outPcm
    }

    /**
     * Finds start and end sample indices of active speech, ignoring leading/trailing silence.
     */
    private fun findSpeechBounds(shorts: ShortArray): Pair<Int, Int> {
        var start = 0
        while (start < shorts.size && abs(shorts[start].toInt()) < SILENCE_THRESHOLD_SHORT) {
            start++
        }
        var end = shorts.size - 1
        while (end >= start && abs(shorts[end].toInt()) < SILENCE_THRESHOLD_SHORT) {
            end--
        }
        // Retain 100ms padding (1600 samples at 16kHz) around speech if bounds exist
        val paddedStart = max(0, start - 1600)
        val paddedEnd = min(shorts.size - 1, end + 1600)
        return Pair(paddedStart, paddedEnd)
    }
}
