package com.myvu.client.ai

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
class GemmaModelDownloaderTest {

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
        val file = GemmaLocalClient.getModelFile(context, fileName)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        createdFiles.add(file)
        return file
    }

    @Test
    fun initialStateReturnsNotDownloadedWhenTargetFileMissing() {
        val downloader = GemmaModelDownloader(context, GemmaLocalClient.GEMMA_4_E2B_LITERT)
        if (downloader.targetFile.exists()) {
            downloader.targetFile.delete()
        }
        val state = downloader.getInitialState()
        assertTrue(state is GemmaDownloadState.NotDownloaded)
    }

    @Test
    fun initialStateReturnsCompletedWhenTargetFileExists() {
        val downloader = GemmaModelDownloader(context, GemmaLocalClient.GEMMA_4_E2B_LITERT)
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 1000L)
        val state = downloader.getInitialState()
        assertTrue(state is GemmaDownloadState.Completed)
    }

    @Test
    fun targetFileUsesLiteRtFileNameForGemma4B() {
        val downloader = GemmaModelDownloader(context, GemmaLocalClient.GEMMA_4_E2B_LITERT)
        assertEquals("gemma-4-E2B-it.litertlm", downloader.targetFile.name)
        assertTrue(downloader.targetFile.name.endsWith(".litertlm"))
    }

    @Test
    fun targetFileUsesBinFileNameForGemma2B() {
        val downloader = GemmaModelDownloader(context, GemmaLocalClient.GEMMA_2B_IT_GPU)
        assertEquals("gemma-2b-it-gpu-int4.bin", downloader.targetFile.name)
        assertTrue(downloader.targetFile.name.endsWith(".bin"))
    }

    @Test
    fun deleteModelRemovesTargetAndTempFiles() {
        val downloader = GemmaModelDownloader(context, GemmaLocalClient.GEMMA_4_E2B_LITERT)
        val target = createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 1000L)
        val temp = File(target.parentFile, "${GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName}.tmp")
        RandomAccessFile(temp, "rw").use { it.setLength(500L) }
        createdFiles.add(temp)

        assertTrue(target.exists())
        assertTrue(temp.exists())

        val deleted = downloader.deleteModel()
        assertTrue(deleted)
        assertFalse(target.exists())
        assertFalse(temp.exists())
        assertTrue(downloader.getInitialState() is GemmaDownloadState.NotDownloaded)
    }
}
