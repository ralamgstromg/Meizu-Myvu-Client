package com.myvu.client.ai

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Codificador liviano de PCM lineal de 16-bit a formato RIFF WAV de 16kHz en memoria.
 */
object WavAudioEncoder {

    @Throws(IOException::class)
    fun encodePcmToWav(pcm: ByteArray, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val baos = ByteArrayOutputStream(44 + pcm.size)
        val dos = DataOutputStream(baos)

        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val chunkSize = 36 + dataSize

        // Chunk ID: "RIFF"
        dos.writeBytes("RIFF")
        // Chunk Size (little-endian)
        dos.writeInt(Integer.reverseBytes(chunkSize))
        // Format: "WAVE"
        dos.writeBytes("WAVE")

        // Subchunk 1 ID: "fmt "
        dos.writeBytes("fmt ")
        // Subchunk 1 Size: 16 para PCM
        dos.writeInt(Integer.reverseBytes(16))
        // Audio Format: 1 (PCM lineal)
        dos.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        // Num Channels: 1 (Mono)
        dos.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
        // Sample Rate (Hz)
        dos.writeInt(Integer.reverseBytes(sampleRate))
        // Byte Rate
        dos.writeInt(Integer.reverseBytes(byteRate))
        // Block Align
        dos.writeShort(java.lang.Short.reverseBytes(blockAlign.toShort()).toInt())
        // Bits Per Sample: 16
        dos.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())

        // Subchunk 2 ID: "data"
        dos.writeBytes("data")
        // Subchunk 2 Size
        dos.writeInt(Integer.reverseBytes(dataSize))
        // Audio Data PCM
        dos.write(pcm)

        dos.flush()
        return baos.toByteArray()
    }
}
