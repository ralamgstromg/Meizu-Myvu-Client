package com.myvu.client.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class InitBurstTest {

    @Test
    fun loadExcludesAiAssistantAndStaleSettings() {
        val assetFile = File("src/main/assets/captured_init.txt")
        val stream = if (assetFile.exists()) {
            FileInputStream(assetFile)
        } else {
            FileInputStream(File("app/src/main/assets/captured_init.txt"))
        }

        val entries = InitBurst.load(stream)
        assertTrue("Entries should not be empty", entries.isNotEmpty())

        for (entry in entries) {
            val body = entry.bodyText()
            assertFalse(
                "Must not contain isContinuousDialogueEnable to avoid active listening battery drain",
                body.contains("isContinuousDialogueEnable")
            )
            assertFalse(
                "Must not contain com.upuphone.ai.assistant init burst",
                body.contains("com.upuphone.ai.assistant")
            )
            assertFalse(
                "Must not contain SyncOffSetTime",
                body.contains("SyncOffSetTime")
            )
            assertFalse(
                "Must not contain sync_clone_data",
                body.contains("sync_clone_data")
            )
        }
    }
}
