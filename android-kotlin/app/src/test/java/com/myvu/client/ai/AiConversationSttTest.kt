package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AiConversationSttTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun sttProviderEnumValuesAndIds() {
        assertEquals("on_device", SttProvider.ON_DEVICE.id)
        assertEquals("Whisper On-Device", SttProvider.ON_DEVICE.label)
        assertEquals("groq", SttProvider.GROQ.id)
        assertEquals("local", SttProvider.LOCAL.id)
    }

    @Test
    fun fromIdReturnsCorrectProviderOrFallback() {
        assertEquals(SttProvider.ON_DEVICE, SttProvider.fromId("on_device"))
        assertEquals(SttProvider.GROQ, SttProvider.fromId("groq"))
        assertEquals(SttProvider.LOCAL, SttProvider.fromId("local"))
        assertEquals(SttProvider.LOCAL, SttProvider.fromId("unknown"))
        assertEquals(SttProvider.LOCAL, SttProvider.fromId(null))
    }

    @Test
    fun usesAndroidSpeechIsActiveForOnDeviceAndAndroid() {
        Prefs.setUseAndroidStt(context, false)
        Prefs.setSttProvider(context, SttProvider.ON_DEVICE.id)

        val conversation = AiConversation(context, { _, _, _ -> })
        // Reflection check for private usesAndroidSpeech property
        val prop = AiConversation::class.java.getDeclaredMethod("getUsesAndroidSpeech")
        prop.isAccessible = true
        val usesSpeechOnDevice = prop.invoke(conversation) as Boolean
        assertTrue(usesSpeechOnDevice)

        Prefs.setSttProvider(context, "android")
        val usesSpeechAndroid = prop.invoke(conversation) as Boolean
        assertTrue(usesSpeechAndroid)

        Prefs.setSttProvider(context, "local")
        val usesSpeechLocalWithoutSetting = prop.invoke(conversation) as Boolean
        assertFalse(usesSpeechLocalWithoutSetting)

        Prefs.setUseAndroidStt(context, true)
        val usesSpeechLocalWithSetting = prop.invoke(conversation) as Boolean
        assertTrue(usesSpeechLocalWithSetting)
    }

    @Test
    fun aiConversationAcceptsInjectedWhisperFactory() {
        var factoryInvoked = false
        val runner = WhisperLocalClient.WhisperInferenceRunner { _, _, _, _, _ ->
            "Transcripción simulada"
        }

        val conversation = AiConversation(
            context = context,
            sender = { _, _, _ -> },
            whisperClientFactory = { ctx, option ->
                factoryInvoked = true
                WhisperLocalClient(ctx, option, runner)
            }
        )

        // Reflection call to transcribe method to verify factory integration
        val transcribeMethod = AiConversation::class.java.getDeclaredMethod(
            "transcribe",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        transcribeMethod.isAccessible = true

        Prefs.setSttProvider(context, SttProvider.ON_DEVICE.id)
        transcribeMethod.invoke(conversation, ByteArray(100), 16000, 1)

        assertTrue(factoryInvoked)
    }
}
