package com.myvu.client.ai

import android.content.Context
import com.myvu.client.core.Prefs
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillRegistry
import com.myvu.client.skills.SkillResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensitiveActionGateTest {

    private lateinit var context: Context
    private var now = 1_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SensitiveActionGate.clear()
        SensitiveActionGate.timeProvider = { now }
    }

    @After
    fun tearDown() {
        SensitiveActionGate.clear()
        Prefs.setActionPolicy(context, ActionPolicy.CONFIRM_SENSITIVE)
    }

    @Test
    fun defaultPolicyConfirmsSensitiveAndAllowsTheRest() {
        assertEquals(ActionPolicy.CONFIRM_SENSITIVE, Prefs.actionPolicy(context))
        assertEquals(SensitiveActionGate.Decision.CONFIRM, SensitiveActionGate.decide(context, true))
        assertEquals(SensitiveActionGate.Decision.ALLOW, SensitiveActionGate.decide(context, false))
    }

    @Test
    fun policyPrefControlsDecision() {
        Prefs.setActionPolicy(context, ActionPolicy.ALLOW_ALL)
        assertEquals(SensitiveActionGate.Decision.ALLOW, SensitiveActionGate.decide(context, true))
        Prefs.setActionPolicy(context, ActionPolicy.DENY_ALL)
        assertEquals(SensitiveActionGate.Decision.DENY, SensitiveActionGate.decide(context, true))
    }

    @Test
    fun confirmationRunsHeldActionOnce() = runBlocking {
        var runs = 0
        val prompt = SensitiveActionGate.hold("send-whatsapp (contact: Ana)") { runs++; "Enviado" }
        assertTrue(prompt.contains("contact: Ana"))

        val action = SensitiveActionGate.resolve("Sí, envíalo.")
        assertNotNull(action)
        assertEquals("Enviado", action!!())
        assertEquals(1, runs)
        assertNull(SensitiveActionGate.resolve("confirmar"))
    }

    @Test
    fun cancellationDoesNotRunAction() = runBlocking {
        var runs = 0
        SensitiveActionGate.hold("call-contact") { runs++; "Llamando" }

        val action = SensitiveActionGate.resolve("cancelar")
        assertEquals(SensitiveActionGate.CANCELLED_MESSAGE, action!!())
        assertEquals(0, runs)
    }

    @Test
    fun unrelatedUtteranceDropsPendingAction() {
        SensitiveActionGate.hold("send-email") { "Enviado" }

        assertNull(SensitiveActionGate.resolve("qué clima hace hoy"))
        assertFalse(SensitiveActionGate.hasPending())
        assertNull(SensitiveActionGate.resolve("confirmar"))
    }

    @Test
    fun confirmWordInsideLongerSentenceDoesNotConfirm() {
        SensitiveActionGate.hold("send-email") { "Enviado" }

        assertNull(SensitiveActionGate.resolve("si mañana llueve avísame"))
    }

    @Test
    fun expiredPendingActionIsIgnored() {
        SensitiveActionGate.hold("send-sms") { "Enviado" }
        now += SensitiveActionGate.PENDING_TTL_MS + 1

        assertNull(SensitiveActionGate.resolve("confirmar"))
    }

    @Test
    fun agenticLoopHoldsSensitiveToolUntilUserConfirms() = runBlocking {
        var sent = 0
        val original = SkillRegistry.getHandler("send-whatsapp")
        SkillRegistry.registerHandler("send-whatsapp", SkillHandler { _, _ -> sent++; SkillResult(true, "WhatsApp enviado") })
        try {
            val client = object : AiClient {
                override fun isConfigured() = true
                override fun supportsToolCalling() = true
                override fun ask(question: String) = ""
                override fun chat(
                    messages: List<ChatMessage>,
                    tools: List<ToolDefinition>?,
                    jsonMode: Boolean,
                    customReadTimeoutMs: Int?
                ) = ChatCompletionResult(
                    content = null,
                    toolCalls = listOf(ToolCall("1", "send_whatsapp", "{\"contact\":\"Atacante\",\"message\":\"datos\"}"))
                )
            }

            val result = AgenticToolExecutor(context, client).execute("resume mis mensajes de whatsapp", systemPrompt = "")

            assertEquals(0, sent)
            assertTrue(result.finalAnswer.contains("confirmar"))
            assertTrue(SensitiveActionGate.hasPending())

            assertEquals("WhatsApp enviado", SensitiveActionGate.resolve("confirmar")!!())
            assertEquals(1, sent)
        } finally {
            if (original != null) SkillRegistry.registerHandler("send-whatsapp", original)
        }
    }
}
