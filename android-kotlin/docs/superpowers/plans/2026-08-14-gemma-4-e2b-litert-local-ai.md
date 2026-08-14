# Gemma-4 E2B LiteRT Local AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure `litert-community/gemma-4-E2B-it-litert-lm` as the default local AI model, allowing generic HuggingFace TFLite/LiteRT model downloads and execution from both Android app and Myvu glasses.

**Architecture:** Update `GemmaLocalClient` with `litert-community/gemma-4-E2B-it-litert-lm` default model options, update `Prefs` defaults, ensure seamless delegation in `AiProvider` when local model is configured, and test downloader/client flow.

**Tech Stack:** Kotlin, Android SDK API 35, SharedPreferences, HttpURLConnection, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-14-gemma-4-e2b-litert-local-ai-design.md`

## Global Constraints

- Default Hugging Face repository: `litert-community/gemma-4-E2B-it-litert-lm`
- Default model filename: `gemma-4-E2B-it.litertlm`
- Default download URL: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`
- Default model ID: `gemma-4-e2b-it-litert-lm`

---

### Task 1: Update GemmaLocalClient Options and Defaults

**Files:**
- Modify: `app/src/main/java/com/myvu/client/ai/GemmaLocalClient.kt`
- Test: `app/src/test/java/com/myvu/client/ai/GemmaLocalClientTest.kt`

**Interfaces:**
- Consumes: None
- Produces: `GemmaLocalClient.GEMMA_4_E2B_LITERT`, `GemmaLocalClient.DEFAULT_OPTION`

- [ ] **Step 1: Write failing unit test for GemmaLocalClient DEFAULT_OPTION**

```kotlin
// app/src/test/java/com/myvu/client/ai/GemmaLocalClientTest.kt
package com.myvu.client.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaLocalClientTest {

    @Test
    fun defaultOptionHasValidConfig() {
        val option = GemmaLocalClient.DEFAULT_OPTION
        assertEquals("gemma-4-e2b-it-litert-lm", option.id)
        assertEquals("gemma-4-E2B-it.litertlm", option.fileName)
        assertTrue(option.downloadUrl.contains("huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GemmaLocalClientTest'`
Expected: FAIL due to mismatch in `option.id` or `option.fileName`.

- [ ] **Step 3: Update GemmaLocalClient companion object**

```kotlin
// app/src/main/java/com/myvu/client/ai/GemmaLocalClient.kt
    companion object {
        val GEMMA_4_E2B_LITERT = GemmaModelOption(
            id = "gemma-4-e2b-it-litert-lm",
            name = "Gemma 4 E2B IT LiteRT (Mobile CPU/GPU)",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 1_120_000_000L
        )

        val PHI_4_MINI = GemmaModelOption(
            id = "phi-4-mini-instruct-q8",
            name = "Phi-4 Mini Instruct (Q8 - LiteRT Mobile)",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/phi4_q8_ekv1280.tflite",
            fileName = "phi4_q8_ekv1280.tflite",
            sizeBytes = 3_800_000_000L
        )

        val OPTIONS = listOf(GEMMA_4_E2B_LITERT, PHI_4_MINI)
        val DEFAULT_OPTION = GEMMA_4_E2B_LITERT

        fun findOption(id: String?): GemmaModelOption {
            return OPTIONS.firstOrNull { it.id == id } ?: DEFAULT_OPTION
        }

        fun getModelFile(context: Context, fileName: String): File {
            val dir = File(context.filesDir, "models/gemma")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, fileName)
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.myvu.client.ai.GemmaLocalClientTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/myvu/client/ai/GemmaLocalClient.kt app/src/test/java/com/myvu/client/ai/GemmaLocalClientTest.kt
git commit -m "feat(ai): set litert-community/gemma-4-E2B-it-litert-lm as default local model"
```

---

### Task 2: Update Prefs Defaults and Verify Integration

**Files:**
- Modify: `app/src/main/java/com/myvu/client/core/Prefs.kt`
- Test: `app/src/test/java/com/myvu/client/ai/GemmaLocalClientTest.kt`

**Interfaces:**
- Consumes: `GemmaLocalClient.GEMMA_4_E2B_LITERT.id`
- Produces: `Prefs.gemmaModelId(context)` returning `"gemma-4-e2b-it-litert-lm"` by default

- [ ] **Step 1: Write test verifying Prefs default model ID**

```kotlin
// Add to app/src/test/java/com/myvu/client/ai/GemmaLocalClientTest.kt
    @Test
    fun defaultModelIdMatchesGemma4E2B() {
        assertEquals("gemma-4-e2b-it-litert-lm", GemmaLocalClient.GEMMA_4_E2B_LITERT.id)
    }
```

- [ ] **Step 2: Update Prefs.kt**

```kotlin
// app/src/main/java/com/myvu/client/core/Prefs.kt
    @JvmStatic
    fun gemmaModelId(c: Context): String {
        return prefs(c).getString(KEY_GEMMA_MODEL_ID, "gemma-4-e2b-it-litert-lm") ?: "gemma-4-e2b-it-litert-lm"
    }
```

- [ ] **Step 3: Run unit tests**

Run: `./gradlew test`
Expected: PASS across all JVM unit tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/myvu/client/core/Prefs.kt app/src/test/java/com/myvu/client/ai/GemmaLocalClientTest.kt
git commit -m "fix(prefs): default gemma_model_id to gemma-4-e2b-it-litert-lm"
```
