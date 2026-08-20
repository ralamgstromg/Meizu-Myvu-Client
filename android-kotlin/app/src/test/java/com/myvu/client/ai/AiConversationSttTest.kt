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
        assertEquals("groq", SttProvider.GROQ.id)
        assertEquals("local", SttProvider.LOCAL.id)
        assertEquals("whisper_cpp", SttProvider.WHISPER_CPP.id)
    }

    @Test
    fun fromIdReturnsCorrectProviderOrFallback() {
        assertEquals(SttProvider.GROQ, SttProvider.fromId("groq"))
        assertEquals(SttProvider.LOCAL, SttProvider.fromId("local"))
        assertEquals(SttProvider.WHISPER_CPP, SttProvider.fromId("whisper_cpp"))
        assertEquals(SttProvider.LOCAL, SttProvider.fromId("unknown"))
        assertEquals(SttProvider.LOCAL, SttProvider.fromId(null))
    }

    @Test
    fun usesAndroidSpeechIsActiveForAndroid() {
        Prefs.setUseAndroidStt(context, false)
        Prefs.setSttProvider(context, "local")

        val conversation = AiConversation(context, { _, _, _ -> })
        val prop = AiConversation::class.java.getDeclaredMethod("getUsesAndroidSpeech")
        prop.isAccessible = true
        val usesSpeechLocalWithoutSetting = prop.invoke(conversation) as Boolean
        assertFalse(usesSpeechLocalWithoutSetting)

        Prefs.setSttProvider(context, "android")
        val usesSpeechAndroid = prop.invoke(conversation) as Boolean
        assertTrue(usesSpeechAndroid)

        Prefs.setUseAndroidStt(context, true)
        val usesSpeechLocalWithSetting = prop.invoke(conversation) as Boolean
        assertTrue(usesSpeechLocalWithSetting)
    }

    @Test
    fun aiConversationAcceptsInjectedWhisperFactory() {
        var factoryInvoked = false

        val conversation = AiConversation(
            context = context,
            sender = { _, _, _ -> },
            whisperClientFactory = { ctx ->
                factoryInvoked = true
                WhisperLocalClient(ctx)
            }
        )

        val transcribeMethod = AiConversation::class.java.getDeclaredMethod(
            "transcribe",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        transcribeMethod.isAccessible = true

        Prefs.setSttProvider(context, SttProvider.LOCAL.id)
        transcribeMethod.invoke(conversation, ByteArray(100), 16000, 1)
    }
}
