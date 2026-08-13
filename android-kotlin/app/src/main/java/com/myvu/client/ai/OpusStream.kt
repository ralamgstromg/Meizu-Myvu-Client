package com.myvu.client.ai

import android.media.MediaCodec
import android.media.MediaFormat
import com.myvu.client.core.LogBus
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes the glasses' Opus packets to 16-bit PCM using MediaCodec. */
object OpusStream {
    const val SAMPLE_RATE: Int = 16000
    const val CHANNELS: Int = 1

    private const val PRE_SKIP_SAMPLES = 3840
    private const val NS_PER_48K_SAMPLE = 1000000000L / 48000L
    private const val DEQUEUE_TIMEOUT_US = 10000L

    @JvmStatic
    @Throws(Exception::class)
    fun decode(packets: List<ByteArray>): ByteArray {
        if (packets.isEmpty()) return ByteArray(0)

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(opusHead()))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(longLe(PRE_SKIP_SAMPLES * NS_PER_48K_SAMPLE)))
        format.setByteBuffer("csd-2", ByteBuffer.wrap(longLe(PRE_SKIP_SAMPLES * NS_PER_48K_SAMPLE)))

        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val pcm = ByteArrayOutputStream()
        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var index = 0
            var presentationUs = 0L
            var inputDone = false

            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inIndex)
                        if (index < packets.size) {
                            val packet = packets[index++]
                            inputBuf?.clear()
                            inputBuf?.put(packet)
                            codec.queueInputBuffer(inIndex, 0, packet.size, presentationUs, 0)
                            presentationUs += 20000
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        val chunk = ByteArray(info.size)
                        outBuf?.position(info.offset)
                        outBuf?.get(chunk)
                        pcm.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (ignored: Exception) {
            }
            codec.release()
        }

        val result = pcm.toByteArray()
        LogBus.log(
            "decoded ${packets.size} Opus packets -> ${result.size / 2} samples (${result.size / 2 * 1000 / SAMPLE_RATE}ms)"
        )
        return result
    }

    @JvmStatic
    fun opusHead(): ByteArray {
        val b = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
        b.put(byteArrayOf('O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(), 'H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte()))
        b.put(1.toByte())
        b.put(CHANNELS.toByte())
        b.putShort(PRE_SKIP_SAMPLES.toShort())
        b.putInt(SAMPLE_RATE)
        b.putShort(0.toShort())
        b.put(0.toByte())
        return b.array()
    }

    @JvmStatic
    fun longLe(value: Long): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
    }

    @JvmStatic
    fun preSkipNs(): ByteArray {
        return longLe(PRE_SKIP_SAMPLES * NS_PER_48K_SAMPLE)
    }

    @JvmStatic
    fun toWav(pcm: ByteArray): ByteArray {
        return toWav(pcm, SAMPLE_RATE, CHANNELS)
    }

    @JvmStatic
    fun toWav(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val dataLen = pcm.size
        val byteRate = sampleRate * channels * 2

        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
        b.putInt(36 + dataLen)
        b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray())
        b.putInt(16)
        b.putShort(1.toShort())
        b.putShort(channels.toShort())
        b.putInt(sampleRate)
        b.putInt(byteRate)
        b.putShort((channels * 2).toShort())
        b.putShort(16.toShort())
        b.put("data".toByteArray())
        b.putInt(dataLen)
        b.put(pcm)
        return b.array()
    }
}
