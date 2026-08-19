package com.myvu.client.ai.engine

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class OnDeviceLlmEngineTest {

    private lateinit var context: Context
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun createTempFile(name: String, content: ByteArray): File {
        val file = File.createTempFile("test_model_", name)
        file.deleteOnExit()
        FileOutputStream(file).use { it.write(content) }
        tempFiles.add(file)
        return file
    }

    private fun createValidLiteRtContainerFile(identifier: String = "LTLM"): File {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        // Offset 0: root table offset (16)
        buffer.putInt(16)
        // Offset 4: 4-byte identifier
        val idBytes = identifier.padEnd(4, ' ').substring(0, 4).toByteArray(Charsets.US_ASCII)
        buffer.put(idBytes)
        // Offset 8..63: dummy payload
        while (buffer.hasRemaining()) {
            buffer.put(0.toByte())
        }
        return createTempFile(".litertlm", buffer.array())
    }

    @After
    fun tearDown() {
        tempFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        tempFiles.clear()
    }

    // ==========================================
    // MediaPipeLlmEngine Tests
    // ==========================================

    @Test
    fun mediaPipeEngineInitialStateIsNotReady() {
        val engine = MediaPipeLlmEngine()
        assertFalse(engine.isReady())
    }

    @Test
    fun mediaPipeEngineGenerateBeforeInitThrowsIOException() {
        val engine = MediaPipeLlmEngine()
        try {
            engine.generate("Hola")
            fail("Expected IOException when engine is not initialized")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("no está inicializado") == true)
        }
    }

    @Test
    fun mediaPipeEngineInitializeNonExistentFileThrowsIOException() {
        val engine = MediaPipeLlmEngine()
        val nonExistentFile = File("/tmp/non_existent_model_file_${System.currentTimeMillis()}.bin")
        try {
            engine.initialize(context, nonExistentFile)
            fail("Expected IOException when model file does not exist")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("no existe") == true)
        }
    }

    @Test
    fun mediaPipeEngineInitializeCatchesNativeFailureAndThrowsIOException() {
        val dummyBinFile = createTempFile(".bin", ByteArray(1024))
        val engine = MediaPipeLlmEngine()
        try {
            // On JVM without native JNI library loaded, initialize will throw IOException wrapping native error
            engine.initialize(context, dummyBinFile)
            fail("Expected IOException on host JVM due to missing native JNI library or invalid model")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("MediaPipe") == true)
        }
    }

    @Test
    fun mediaPipeEngineCloseResetsReadyState() {
        val engine = MediaPipeLlmEngine()
        engine.close()
        assertFalse(engine.isReady())
    }

    // ==========================================
    // LiteRtLmEngine Tests
    // ==========================================

    @Test
    fun liteRtEngineInitialStateIsNotReady() {
        val engine = LiteRtLmEngine()
        assertFalse(engine.isReady())
        assertNull(engine.getContainerMetadata())
    }

    @Test
    fun liteRtEngineGenerateBeforeInitThrowsIOException() {
        val engine = LiteRtLmEngine()
        try {
            engine.generate("Test prompt")
            fail("Expected IOException when engine is not initialized")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("no está inicializado") == true)
        }
    }

    @Test
    fun liteRtEngineInitializeNonExistentFileThrowsIOException() {
        val engine = LiteRtLmEngine()
        val nonExistentFile = File("/tmp/non_existent_${System.currentTimeMillis()}.litertlm")
        try {
            engine.initialize(context, nonExistentFile)
            fail("Expected IOException when model file does not exist")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("no existe") == true)
        }
    }

    @Test
    fun liteRtEngineInitializeTooSmallFileThrowsIOException() {
        val smallFile = createTempFile(".litertlm", byteArrayOf(1, 2, 3))
        val engine = LiteRtLmEngine()

        try {
            engine.initialize(context, smallFile)
            fail("Expected IOException when file is smaller than minimum container size")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("demasiado pequeño") == true)
        }
    }

    @Test
    fun liteRtEngineInitializeInvalidFlatBufferOffsetThrowsIOException() {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0) // Invalid offset 0
        buffer.put("LTLM".toByteArray(Charsets.US_ASCII))
        val invalidFile = createTempFile(".litertlm", buffer.array())

        val engine = LiteRtLmEngine()

        try {
            engine.initialize(context, invalidFile)
            fail("Expected IOException when FlatBuffer root offset is invalid")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Encabezado FlatBuffer inválido") == true)
        }
    }

    @Test
    fun liteRtEngineInitializeValidContainerParsesMetadata() {
        val validFile = createValidLiteRtContainerFile("GEM4")
        val engine = LiteRtLmEngine()

        engine.initialize(context, validFile, maxTokens = 256)

        assertTrue(engine.isReady())
        val metadata = engine.getContainerMetadata()
        assertNotNull(metadata)
        assertEquals(validFile.name, metadata?.fileName)
        assertEquals(64L, metadata?.fileSizeBytes)
        assertEquals(16L, metadata?.rootTableOffset)
        assertEquals("GEM4", metadata?.identifier)
    }

    @Test
    fun liteRtEngineGenerateWithCustomRunnerExecutesSuccessfully() {
        val validFile = createValidLiteRtContainerFile("LTLM")
        val customRunner = LiteRtLmEngine.LiteRtInferenceRunner { file, prompt, maxTokens ->
            "Response to: $prompt (file: ${file.name}, max: $maxTokens)"
        }
        val engine = LiteRtLmEngine(inferenceRunner = customRunner)

        engine.initialize(context, validFile, maxTokens = 128)
        assertTrue(engine.isReady())

        val response = engine.generate("¿Cuál es la capital de Francia?")
        assertEquals("Response to: ¿Cuál es la capital de Francia? (file: ${validFile.name}, max: 128)", response)
    }

    @Test
    fun liteRtEngineGenerateBlankPromptReturnsEmptyString() {
        var runnerCalled = false
        val customRunner = LiteRtLmEngine.LiteRtInferenceRunner { _, _, _ ->
            runnerCalled = true
            "Response"
        }
        val engine = LiteRtLmEngine(inferenceRunner = customRunner)
        val validFile = createValidLiteRtContainerFile("LTLM")

        engine.initialize(context, validFile)
        val response = engine.generate("   ")
        assertEquals("", response)
        assertFalse("Runner should not be called for blank prompt", runnerCalled)
    }

    @Test
    fun liteRtEngineGenerateEmptyResponseThrowsIOException() {
        val customRunner = LiteRtLmEngine.LiteRtInferenceRunner { _, _, _ ->
            "   "
        }
        val engine = LiteRtLmEngine(inferenceRunner = customRunner)
        val validFile = createValidLiteRtContainerFile("LTLM")

        engine.initialize(context, validFile)
        try {
            engine.generate("Hello")
            fail("Expected IOException when runner returns empty response")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("respuesta vacía") == true)
        }
    }

    @Test
    fun liteRtEngineGenerateDefaultRunnerThrowsDiagnosticIOException() {
        val validFile = createValidLiteRtContainerFile("LTLM")
        val engine = LiteRtLmEngine()

        engine.initialize(context, validFile)
        try {
            engine.generate("Test prompt")
            fail("Expected IOException when default native runner is executed in test environment")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("LiteRT-LM") == true)
        }
    }

    @Test
    fun liteRtEngineCloseResetsState() {
        val validFile = createValidLiteRtContainerFile("LTLM")
        val engine = LiteRtLmEngine()

        engine.initialize(context, validFile)
        assertTrue(engine.isReady())

        engine.close()
        assertFalse(engine.isReady())
        assertNull(engine.getContainerMetadata())
    }

    @Test
    fun liteRtEngineIsNativeRunnerAvailableReflectsConfiguration() {
        val defaultEngine = LiteRtLmEngine()
        assertFalse(defaultEngine.isNativeRunnerAvailable())

        val customRunner = LiteRtLmEngine.LiteRtInferenceRunner { _, _, _ -> "ok" }
        val engineWithRunner = LiteRtLmEngine(inferenceRunner = customRunner)
        assertTrue(engineWithRunner.isNativeRunnerAvailable())
    }
}
