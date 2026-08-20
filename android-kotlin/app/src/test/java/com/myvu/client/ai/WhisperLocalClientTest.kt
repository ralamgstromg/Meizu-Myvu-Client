package com.myvu.client.ai

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
class WhisperLocalClientTest {

    private lateinit var context: Context
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        createdFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        createdFiles.clear()
    }

    private fun createSparseModelFile(fileName: String, sizeBytes: Long): File {
        val file = WhisperLocalClient.getModelFile(context, fileName)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        createdFiles.add(file)
        return file
    }

    @Test
    fun defaultOptionHasValidConfig() {
        val option = WhisperLocalClient.DEFAULT_OPTION
        assertEquals("whisper-large-v3-turbo-i4", option.id)
        assertEquals("Whisper Large v3 Turbo (INT4 LiteRT ~721MB)", option.name)
        assertEquals("whisper_large_v3_turbo_30s_i4.tflite", option.fileName)
        assertEquals(721_000_000L, option.sizeBytes)
        assertTrue(option.downloadUrl.contains("huggingface.co/litert-community/whisper-large-v3-turbo"))
        assertTrue(option.downloadUrl.endsWith("whisper_large_v3_turbo_30s_i4.tflite"))
    }

    @Test
    fun tinyOptionHasValidConfig() {
        val option = WhisperLocalClient.WHISPER_TINY_ACFT
        assertEquals("whisper-tiny-acft", option.id)
        assertEquals("Whisper Tiny ACFT (LiteRT ~75MB)", option.name)
        assertEquals("whisper-tiny-acft.tflite", option.fileName)
        assertEquals(75_000_000L, option.sizeBytes)
        assertTrue(option.downloadUrl.contains("huggingface.co/litert-community/whisper-acft"))
        assertTrue(option.downloadUrl.endsWith("whisper-tiny-acft.tflite"))
    }

    @Test
    fun optionsCatalogContainsAllModels() {
        val options = WhisperLocalClient.OPTIONS
        assertEquals(2, options.size)
        assertTrue(options.contains(WhisperLocalClient.WHISPER_LARGE_V3_TURBO_I4))
        assertTrue(options.contains(WhisperLocalClient.WHISPER_TINY_ACFT))
    }

    @Test
    fun findOptionReturnsMatchingOrFallback() {
        val tiny = WhisperLocalClient.findOption("whisper-tiny-acft")
        assertEquals(WhisperLocalClient.WHISPER_TINY_ACFT, tiny)

        val large = WhisperLocalClient.findOption("whisper-large-v3-turbo-i4")
        assertEquals(WhisperLocalClient.WHISPER_LARGE_V3_TURBO_I4, large)

        val unknown = WhisperLocalClient.findOption("non-existent-id")
        assertEquals(WhisperLocalClient.DEFAULT_OPTION, unknown)

        val nullOption = WhisperLocalClient.findOption(null)
        assertEquals(WhisperLocalClient.DEFAULT_OPTION, nullOption)
    }

    @Test
    fun isConfiguredReturnsFalseWhenModelFileDoesNotExist() {
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        assertFalse(client.isConfigured())
    }

    @Test
    fun isConfiguredReturnsFalseWhenModelFileIsTooSmall() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 500L)
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        assertFalse(client.isConfigured())
    }

    @Test
    fun isConfiguredReturnsTrueWhenModelFileHasSufficientSize() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 75_000_000L)
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        assertTrue(client.isConfigured())
    }

    @Test
    fun transcribeThrowsWhenModelNotDownloaded() {
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        try {
            client.transcribe(byteArrayOf(1, 2, 3), 16000, 1, "es")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("no descargado") == true)
        }
    }

    @Test
    fun transcribeThrowsWhenModelFileIsIncomplete() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 500L)
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        try {
            client.transcribe(byteArrayOf(1, 2, 3), 16000, 1, "es")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("incompleto o corrupto") == true)
        }
    }

    @Test
    fun transcribeReturnsEmptyStringOnEmptyAudio() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 75_000_000L)
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        val result = client.transcribe(ByteArray(0), 16000, 1, "es")
        assertEquals("", result)
    }

    @Test
    fun isRunnerAvailableReturnsCorrectState() {
        val clientWithoutRunner = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)
        assertFalse(clientWithoutRunner.isRunnerAvailable())

        val clientWithRunner = WhisperLocalClient(
            context,
            WhisperLocalClient.WHISPER_TINY_ACFT,
            inferenceRunner = { _, _, _, _, _ -> "test" }
        )
        assertTrue(clientWithRunner.isRunnerAvailable())
    }

    @Test
    fun transcribeExecutesInjectedRunnerSuccessfully() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 75_000_000L)
        var receivedLang: String? = null
        var receivedRate: Int? = null
        var receivedChannels: Int? = null
        var receivedPcmSize: Int? = null

        val runner = WhisperLocalClient.WhisperInferenceRunner { modelFile, pcm, sampleRate, channels, language ->
            receivedLang = language
            receivedRate = sampleRate
            receivedChannels = channels
            receivedPcmSize = pcm.size
            "Hola mundo desde Whisper on-device"
        }

        val client = WhisperLocalClient(
            context,
            WhisperLocalClient.WHISPER_TINY_ACFT,
            inferenceRunner = runner
        )

        val fakePcm = ByteArray(3200) { 0 }
        val transcript = client.transcribe(fakePcm, 16000, 1, "es")

        assertEquals("Hola mundo desde Whisper on-device", transcript)
        assertEquals("es", receivedLang)
        assertEquals(16000, receivedRate)
        assertEquals(1, receivedChannels)
        assertEquals(3200, receivedPcmSize)
    }

    @Test
    fun transcribeThrowsIOExceptionWhenRunnerReturnsBlank() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 75_000_000L)
        val runner = WhisperLocalClient.WhisperInferenceRunner { _, _, _, _, _ ->
            "   \n  "
        }
        val client = WhisperLocalClient(
            context,
            WhisperLocalClient.WHISPER_TINY_ACFT,
            inferenceRunner = runner
        )

        try {
            client.transcribe(ByteArray(100), 16000, 1, "es")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("transcripción vacía") == true)
        }
    }

    @Test
    fun transcribeThrowsIOExceptionWhenRunnerThrows() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 75_000_000L)
        val runner = WhisperLocalClient.WhisperInferenceRunner { _, _, _, _, _ ->
            throw IllegalStateException("LiteRT tensor execution failed")
        }
        val client = WhisperLocalClient(
            context,
            WhisperLocalClient.WHISPER_TINY_ACFT,
            inferenceRunner = runner
        )

        try {
            client.transcribe(ByteArray(100), 16000, 1, "es")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Error en inferencia local Whisper") == true)
        }
    }

    @Test
    fun transcribeThrowsFallbackIOExceptionWhenNativeRunnerNotAvailable() {
        createSparseModelFile(WhisperLocalClient.WHISPER_TINY_ACFT.fileName, 75_000_000L)
        val client = WhisperLocalClient(context, WhisperLocalClient.WHISPER_TINY_ACFT)

        try {
            client.transcribe(ByteArray(16000), 16000, 1, "es")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Activando fallback") == true)
        }
    }
}
