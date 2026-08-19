package com.myvu.client.ai

import android.content.Context
import com.myvu.client.ai.engine.LiteRtLmEngine
import com.myvu.client.ai.engine.MediaPipeLlmEngine
import com.myvu.client.ai.engine.OnDeviceLlmEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class GemmaLocalClientTest {

    private lateinit var context: Context
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        GemmaLocalClient.clearCache()
    }

    @After
    fun tearDown() {
        GemmaLocalClient.clearCache()
        createdFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        createdFiles.clear()
    }

    private fun createSparseModelFile(fileName: String, sizeBytes: Long): File {
        val file = GemmaLocalClient.getModelFile(context, fileName)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        createdFiles.add(file)
        return file
    }

    @Test
    fun gemma4E2BOptionHasValidConfigAndLiteRtType() {
        val option = GemmaLocalClient.GEMMA_4_E2B_LITERT
        assertEquals("gemma-4-e2b-it-litert-lm", option.id)
        assertEquals("Gemma 4 E2B IT (LiteRT-LM ~1.12GB)", option.name)
        assertEquals("gemma-4-E2B-it.litertlm", option.fileName)
        assertEquals(1_120_000_000L, option.sizeBytes)
        assertEquals(GemmaEngineType.LITERT_LM, option.engineType)
        assertTrue(option.downloadUrl.contains("litert-community/gemma-4-E2B-it-litert-lm"))
        assertTrue(option.downloadUrl.endsWith("gemma-4-E2B-it.litertlm"))
    }

    @Test
    fun defaultOptionHasValidConfig() {
        val option = GemmaLocalClient.DEFAULT_OPTION
        assertEquals("gemma-2b-it-gpu-int4", option.id)
        assertEquals("gemma-2b-it-gpu-int4.bin", option.fileName)
        assertEquals(GemmaEngineType.MEDIAPIPE, option.engineType)
        assertTrue(option.downloadUrl.contains("huggingface.co/google/gemma-2b-it-tflite"))
    }

    @Test
    fun googleAiEdgeGalleryOptionsAreAvailable() {
        assertEquals("gemma-2b-it-gpu-int4", GemmaLocalClient.GEMMA_2B_IT_GPU.id)
        assertEquals("gemma-2b-it-cpu-int4", GemmaLocalClient.GEMMA_2B_IT_CPU.id)
        assertEquals("gemma-2-2b-it-gpu-int4", GemmaLocalClient.GEMMA_2_2B_IT_GPU.id)
        assertEquals("gemma-1.1-2b-it-gpu-int4", GemmaLocalClient.GEMMA_1_1_2B_IT_GPU.id)

        assertEquals(GemmaEngineType.MEDIAPIPE, GemmaLocalClient.GEMMA_2B_IT_GPU.engineType)
        assertEquals(GemmaEngineType.MEDIAPIPE, GemmaLocalClient.GEMMA_2B_IT_CPU.engineType)
        assertEquals(GemmaEngineType.MEDIAPIPE, GemmaLocalClient.GEMMA_2_2B_IT_GPU.engineType)
        assertEquals(GemmaEngineType.MEDIAPIPE, GemmaLocalClient.GEMMA_1_1_2B_IT_GPU.engineType)
    }

    @Test
    fun optionsCatalogContainsAllFiveModels() {
        val options = GemmaLocalClient.OPTIONS
        assertEquals(5, options.size)
        assertTrue(options.contains(GemmaLocalClient.GEMMA_4_E2B_LITERT))
        assertTrue(options.contains(GemmaLocalClient.GEMMA_2B_IT_GPU))
        assertTrue(options.contains(GemmaLocalClient.GEMMA_2_2B_IT_GPU))
        assertTrue(options.contains(GemmaLocalClient.GEMMA_2B_IT_CPU))
        assertTrue(options.contains(GemmaLocalClient.GEMMA_1_1_2B_IT_GPU))
    }

    @Test
    fun findOptionReturnsMatchingOrFallback() {
        val gemma4 = GemmaLocalClient.findOption("gemma-4-e2b-it-litert-lm")
        assertEquals("gemma-4-e2b-it-litert-lm", gemma4.id)
        assertEquals("gemma-4-E2B-it.litertlm", gemma4.fileName)
        assertEquals(GemmaEngineType.LITERT_LM, gemma4.engineType)

        val found = GemmaLocalClient.findOption("gemma-2-2b-it-gpu-int4")
        assertEquals("gemma-2-2b-it-gpu-int4.bin", found.fileName)
        assertEquals(GemmaEngineType.MEDIAPIPE, found.engineType)

        val fallback = GemmaLocalClient.findOption("unknown_model_id")
        assertEquals(GemmaLocalClient.DEFAULT_OPTION.id, fallback.id)

        val fallbackNull = GemmaLocalClient.findOption(null)
        assertEquals(GemmaLocalClient.DEFAULT_OPTION.id, fallbackNull.id)
    }

    @Test
    fun createEngineReturnsCorrectEngineInstance() {
        val liteRtEngine = GemmaLocalClient.createEngine(GemmaLocalClient.GEMMA_4_E2B_LITERT)
        assertTrue(liteRtEngine is LiteRtLmEngine)

        val mediaPipeGpu = GemmaLocalClient.createEngine(GemmaLocalClient.GEMMA_2B_IT_GPU)
        assertTrue(mediaPipeGpu is MediaPipeLlmEngine)

        val mediaPipe2B2 = GemmaLocalClient.createEngine(GemmaLocalClient.GEMMA_2_2B_IT_GPU)
        assertTrue(mediaPipe2B2 is MediaPipeLlmEngine)

        val mediaPipeCpu = GemmaLocalClient.createEngine(GemmaLocalClient.GEMMA_2B_IT_CPU)
        assertTrue(mediaPipeCpu is MediaPipeLlmEngine)

        val mediaPipe11 = GemmaLocalClient.createEngine(GemmaLocalClient.GEMMA_1_1_2B_IT_GPU)
        assertTrue(mediaPipe11 is MediaPipeLlmEngine)
    }

    @Test
    fun isConfiguredValidation() {
        val client = GemmaLocalClient(context, GemmaLocalClient.GEMMA_4_E2B_LITERT)

        // Case 1: File does not exist
        assertFalse(client.isConfigured())

        // Case 2: File exists but is too small (< 50MB)
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 10_000_000L)
        assertFalse(client.isConfigured())

        // Case 3: File exists and is > 50MB
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 60_000_000L)
        assertTrue(client.isConfigured())
    }

    @Test
    fun askThrowsIOExceptionWhenNotConfigured() {
        val client = GemmaLocalClient(context, GemmaLocalClient.GEMMA_4_E2B_LITERT)
        try {
            client.ask("¿Cuál es la capital de Colombia?")
            fail("Expected IOException when model is not configured")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("no descargado o incompleto") == true)
            assertTrue(e.message?.contains("gemma-4-E2B-it.litertlm") == true)
        }
    }

    @Test
    fun askWithCustomEngineExecutesSuccessfully() {
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 60_000_000L)

        var generateCalled = false
        val testEngine = object : OnDeviceLlmEngine {
            private var ready = false
            override fun initialize(context: Context, modelFile: File, maxTokens: Int) {
                ready = true
            }
            override fun generate(prompt: String): String {
                generateCalled = true
                return "Respuesta simulada para: $prompt"
            }
            override fun isReady(): Boolean = ready
            override fun close() {
                ready = false
            }
        }

        val client = GemmaLocalClient(context, GemmaLocalClient.GEMMA_4_E2B_LITERT, customEngine = testEngine)
        assertTrue(client.isConfigured())

        val result = client.ask("Hola Gemma")
        assertTrue(generateCalled)
        assertEquals("Respuesta simulada para: Hola Gemma", result)
    }

    @Test
    fun askWithEngineReturningBlankThrowsIOException() {
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 60_000_000L)

        val testEngine = object : OnDeviceLlmEngine {
            override fun initialize(context: Context, modelFile: File, maxTokens: Int) {}
            override fun generate(prompt: String): String = "   "
            override fun isReady(): Boolean = true
            override fun close() {}
        }

        val client = GemmaLocalClient(context, GemmaLocalClient.GEMMA_4_E2B_LITERT, customEngine = testEngine)

        try {
            client.ask("Hola Gemma")
            fail("Expected IOException when engine returns blank")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("retornó una respuesta vacía") == true || e.message?.contains("Activando fallback") == true)
        }
    }

    @Test
    fun askWithEngineThrowingErrorWrapsInIOException() {
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 60_000_000L)

        val testEngine = object : OnDeviceLlmEngine {
            override fun initialize(context: Context, modelFile: File, maxTokens: Int) {}
            override fun generate(prompt: String): String = throw RuntimeException("Out of memory on GPU")
            override fun isReady(): Boolean = true
            override fun close() {}
        }

        val client = GemmaLocalClient(context, GemmaLocalClient.GEMMA_4_E2B_LITERT, customEngine = testEngine)

        try {
            client.ask("Hola Gemma")
            fail("Expected IOException wrapping runtime error")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Inferencia local Gemma falló") == true)
            assertTrue(e.message?.contains("Out of memory on GPU") == true)
        }
    }
}
