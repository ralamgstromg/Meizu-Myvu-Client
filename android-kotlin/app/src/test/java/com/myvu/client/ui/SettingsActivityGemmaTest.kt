package com.myvu.client.ui

import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.myvu.client.R
import com.myvu.client.ai.GemmaLocalClient
import com.myvu.client.core.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
class SettingsActivityGemmaTest {

    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        GemmaLocalClient.clearCache()
        Prefs.setGemmaModelId(context, GemmaLocalClient.GEMMA_4_E2B_LITERT.id)
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
        val context = RuntimeEnvironment.getApplication()
        val file = GemmaLocalClient.getModelFile(context, fileName)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        createdFiles.add(file)
        return file
    }

    @Test
    fun activityInitializesWithGemma4BSelectedWhenConfiguredInPrefs() {
        val context = RuntimeEnvironment.getApplication()
        Prefs.setGemmaModelId(context, GemmaLocalClient.GEMMA_4_E2B_LITERT.id)

        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val group = activity.findViewById<MaterialButtonToggleGroup>(R.id.btnGemmaModelVersionGroup)
        assertNotNull(group)
        assertEquals(R.id.btnGemma4B, group.checkedButtonId)

        val lblStatus = activity.findViewById<TextView>(R.id.lblGemmaModelStatus)
        assertNotNull(lblStatus)
        assertTrue(lblStatus.text.contains("LITERT_LM"))
        assertTrue(lblStatus.text.contains("Gemma 4 E2B"))
    }

    @Test
    fun selectingGemma2BGpuUpdatesPrefsAndStatusWithMediaPipeEngine() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val group = activity.findViewById<MaterialButtonToggleGroup>(R.id.btnGemmaModelVersionGroup)
        group.check(R.id.btnGemma2B)

        val savedId = Prefs.gemmaModelId(activity)
        assertEquals(GemmaLocalClient.GEMMA_2B_IT_GPU.id, savedId)

        val lblStatus = activity.findViewById<TextView>(R.id.lblGemmaModelStatus)
        assertTrue(lblStatus.text.contains("MEDIAPIPE"))
        assertTrue(lblStatus.text.contains("Gemma 2B IT"))
    }

    @Test
    fun selectingGemma2B2UpdatesPrefsAndStatusWithMediaPipeEngine() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val group = activity.findViewById<MaterialButtonToggleGroup>(R.id.btnGemmaModelVersionGroup)
        group.check(R.id.btnGemma2B2)

        val savedId = Prefs.gemmaModelId(activity)
        assertEquals(GemmaLocalClient.GEMMA_2_2B_IT_GPU.id, savedId)

        val lblStatus = activity.findViewById<TextView>(R.id.lblGemmaModelStatus)
        assertTrue(lblStatus.text.contains("MEDIAPIPE"))
        assertTrue(lblStatus.text.contains("Gemma 2 2B IT"))
    }

    @Test
    fun selectingGemma2BCpuUpdatesPrefsAndStatusWithMediaPipeEngine() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val group = activity.findViewById<MaterialButtonToggleGroup>(R.id.btnGemmaModelVersionGroup)
        group.check(R.id.btnGemma2BCpu)

        val savedId = Prefs.gemmaModelId(activity)
        assertEquals(GemmaLocalClient.GEMMA_2B_IT_CPU.id, savedId)

        val lblStatus = activity.findViewById<TextView>(R.id.lblGemmaModelStatus)
        assertTrue(lblStatus.text.contains("MEDIAPIPE"))
        assertTrue(lblStatus.text.contains("Gemma 2B IT (Google AI Edge CPU"))
    }

    @Test
    fun statusDisplaysReadyWhenModelFileExists() {
        createSparseModelFile(GemmaLocalClient.GEMMA_4_E2B_LITERT.fileName, 60_000_000L)
        val context = RuntimeEnvironment.getApplication()
        Prefs.setGemmaModelId(context, GemmaLocalClient.GEMMA_4_E2B_LITERT.id)

        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()

        val lblStatus = activity.findViewById<TextView>(R.id.lblGemmaModelStatus)
        assertTrue(lblStatus.text.contains("Listo para uso offline"))
        assertTrue(lblStatus.text.contains("[LITERT_LM]"))
    }
}
