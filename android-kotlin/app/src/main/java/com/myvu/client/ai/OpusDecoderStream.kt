package com.myvu.client.ai

import android.media.MediaCodec
import android.media.MediaFormat
import com.myvu.client.core.BufferPool
import com.myvu.client.core.LogBus
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** A stateful Opus decoder that consumes packets as they arrive. */
class OpusDecoderStream {

    fun interface PcmConsumer {
        fun onPcm(buffer: ByteArray, length: Int)
    }

    private var codec: MediaCodec? = null
    private var outputSampleRate = OpusStream.SAMPLE_RATE
    private var outputChannels = OpusStream.CHANNELS
    private var presentationUs = 0L
    private val all = ByteArrayOutputStream()

    private val bufferInfo = MediaCodec.BufferInfo()

    @Throws(Exception::class)
    fun start() {
        stop()
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS, OpusStream.SAMPLE_RATE, OpusStream.CHANNELS
        )
        format.setByteBuffer("csd-0", ByteBuffer.wrap(OpusStream.opusHead()))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(OpusStream.preSkipNs()))
        format.setByteBuffer("csd-2", ByteBuffer.wrap(OpusStream.preSkipNs()))

        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        codec?.configure(format, null, null, 0)
        codec?.start()
        presentationUs = 0
        outputSampleRate = OpusStream.SAMPLE_RATE
        outputChannels = OpusStream.CHANNELS
        all.reset()
    }

    @JvmOverloads
    fun feed(packet: ByteArray?, length: Int = packet?.size ?: 0): ByteArray {
        if (codec == null || packet == null || length <= 0) return EMPTY_PCM
        try {
            val out = ByteArrayOutputStream()
            var queued = false
            var attempt = 0
            while (attempt < 100 && !queued) {
                val inIndex = codec?.dequeueInputBuffer(DEQUEUE_TIMEOUT_US) ?: -1
                if (inIndex >= 0) {
                    val inputBuf = codec?.getInputBuffer(inIndex)
                    inputBuf?.clear()
                    inputBuf?.put(packet, 0, length)
                    codec?.queueInputBuffer(inIndex, 0, length, presentationUs, 0)
                    presentationUs += 20000
                    queued = true
                }
                val pcm = drain()
                out.write(pcm, 0, pcm.size)
                attempt++
            }
            if (!queued) LogBus.warn("Opus decoder never freed an input buffer -- packet lost")
            return out.toByteArray()
        } catch (e: Exception) {
            LogBus.error("Opus decode failed", e)
            return EMPTY_PCM
        }
    }

    fun feed(packet: ByteArray?, length: Int, consumer: PcmConsumer) {
        if (codec == null || packet == null || length <= 0) return
        try {
            var queued = false
            var attempt = 0
            while (attempt < 100 && !queued) {
                val inIndex = codec?.dequeueInputBuffer(DEQUEUE_TIMEOUT_US) ?: -1
                if (inIndex >= 0) {
                    val inputBuf = codec?.getInputBuffer(inIndex)
                    inputBuf?.clear()
                    inputBuf?.put(packet, 0, length)
                    codec?.queueInputBuffer(inIndex, 0, length, presentationUs, 0)
                    presentationUs += 20000
                    queued = true
                }
                drainToConsumer(consumer)
                attempt++
            }
            if (!queued) LogBus.warn("Opus decoder never freed an input buffer -- packet lost")
        } catch (e: Exception) {
            LogBus.error("Opus decode failed", e)
        }
    }

    fun finish() {
        val c = codec ?: return
        try {
            val inIndex = c.dequeueInputBuffer(50000)
            if (inIndex >= 0) {
                c.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            val info = MediaCodec.BufferInfo()
            for (i in 0 until 200) {
                val outIndex = c.dequeueOutputBuffer(info, 20000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                if (outIndex < 0) {
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    continue
                }
                if (info.size > 0) {
                    val buf = c.getOutputBuffer(outIndex)
                    val chunk = BufferPool.obtain(info.size)
                    buf?.position(info.offset)
                    buf?.get(chunk, 0, info.size)
                    all.write(chunk, 0, info.size)
                    BufferPool.recycle(chunk)
                }
                c.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        } catch (e: Exception) {
            LogBus.error("Opus decoder flush failed", e)
        }
    }

    private fun drain(): ByteArray {
        val c = codec ?: return EMPTY_PCM
        val out = ByteArrayOutputStream()
        while (true) {
            val outIndex = c.dequeueOutputBuffer(bufferInfo, 0)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val actual = c.outputFormat
                outputSampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                outputChannels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                LogBus.log(
                    "Opus decoder output: ${outputSampleRate}Hz, ${outputChannels}ch" +
                            if (outputSampleRate != OpusStream.SAMPLE_RATE) " (NOT the declared ${OpusStream.SAMPLE_RATE}Hz)" else ""
                )
                continue
            }
            if (outIndex < 0) break
            if (bufferInfo.size > 0) {
                val buf = c.getOutputBuffer(outIndex)
                val chunk = BufferPool.obtain(bufferInfo.size)
                buf?.position(bufferInfo.offset)
                buf?.get(chunk, 0, bufferInfo.size)
                out.write(chunk, 0, bufferInfo.size)
                all.write(chunk, 0, bufferInfo.size)
                BufferPool.recycle(chunk)
            }
            try {
                c.releaseOutputBuffer(outIndex, false)
            } catch (ignored: Exception) {
            }
        }
        return out.toByteArray()
    }

    private fun drainToConsumer(consumer: PcmConsumer?) {
        val c = codec ?: return
        while (true) {
            val outIndex = c.dequeueOutputBuffer(bufferInfo, 0)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val actual = c.outputFormat
                outputSampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                outputChannels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                LogBus.log(
                    "Opus decoder output: ${outputSampleRate}Hz, ${outputChannels}ch" +
                            if (outputSampleRate != OpusStream.SAMPLE_RATE) " (NOT the declared ${OpusStream.SAMPLE_RATE}Hz)" else ""
                )
                continue
            }
            if (outIndex < 0) break
            if (bufferInfo.size > 0) {
                val buf = c.getOutputBuffer(outIndex)
                val chunk = BufferPool.obtain(bufferInfo.size)
                buf?.position(bufferInfo.offset)
                buf?.get(chunk, 0, bufferInfo.size)
                all.write(chunk, 0, bufferInfo.size)
                consumer?.onPcm(chunk, bufferInfo.size)
                BufferPool.recycle(chunk)
            }
            try {
                c.releaseOutputBuffer(outIndex, false)
            } catch (ignored: Exception) {
            }
        }
    }

    fun allPcm(): ByteArray = all.toByteArray()

    fun sampleRate(): Int = outputSampleRate

    fun channels(): Int = outputChannels

    fun stop() {
        if (codec == null) return
        try {
            codec?.stop()
        } catch (ignored: Exception) {
        }
        try {
            codec?.release()
        } catch (ignored: Exception) {
        }
        codec = null
    }

    companion object {
        private const val DEQUEUE_TIMEOUT_US = 5000L
        private val EMPTY_PCM = ByteArray(0)

        @JvmStatic
        fun energy(pcm: ByteArray): Double {
            if (pcm.size < 2) return 0.0
            var sum = 0L
            val samples = pcm.size / 2
            var i = 0
            while (i + 1 < pcm.size) {
                val sample = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort()
                sum += Math.abs(sample.toInt()).toLong()
                i += 2
            }
            return sum.toDouble() / samples
        }
    }
}
