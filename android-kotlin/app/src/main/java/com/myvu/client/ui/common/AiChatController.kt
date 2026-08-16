package com.myvu.client.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.myvu.client.R
import com.myvu.client.core.LogBus
import com.myvu.client.core.setMarkdown

/**
 * Reusable, decoupled controller for interactive AI Chat across notes, reminders, and recordings:
 * - Cyberpunk styled message bubbles (User vs AI)
 * - Markdown rendering for formatted AI answers
 * - Loading indicator bubble
 * - Auto scroll to bottom
 */
class AiChatController(
    private val activity: AppCompatActivity,
    private val layChatMessages: LinearLayout,
    private val scrollChat: ScrollView,
    private val etQuestion: EditText,
    private val btnSend: MaterialButton,
    private val onExecuteAiQuery: (question: String, onComplete: (answer: String?) -> Unit) -> Unit
) {

    init {
        btnSend.setOnClickListener {
            sendCurrentQuestion()
        }
    }

    fun setQuestionText(text: String) {
        etQuestion.setText(text)
        etQuestion.setSelection(text.length)
        etQuestion.requestFocus()
    }

    private fun sendCurrentQuestion() {
        val question = etQuestion.text.toString().trim()
        if (question.isBlank()) return

        etQuestion.setText("")
        addUserMessage(question)

        val loadingBubble = addLoadingBubble()
        btnSend.isEnabled = false

        onExecuteAiQuery(question) { answer ->
            activity.runOnUiThread {
                layChatMessages.removeView(loadingBubble)
                btnSend.isEnabled = true
                if (answer != null) {
                    addAiMessage(answer)
                } else {
                    addAiMessage("⚠️ No se pudo obtener respuesta de la IA. Por favor, verifica tu clave API en Ajustes.")
                }
            }
        }
    }

    fun addUserMessage(text: String) {
        val bubble = TextView(activity).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.on_cyber_teal))
            val bg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.cyber_teal))
                cornerRadius = 16f
            }
            background = bg
            setPadding(32, 20, 32, 20)
            textSize = 14f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 64
                topMargin = 12
                bottomMargin = 12
                gravity = android.view.Gravity.END
            }
            layoutParams = params
        }
        layChatMessages.addView(bubble)
        scrollToBottom()
    }

    fun addAiMessage(text: String) {
        val bubble = TextView(activity).apply {
            setMarkdown(text)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_obsidian))
            val bg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.obsidian_container_high))
                cornerRadius = 16f
                setStroke(2, ContextCompat.getColor(context, R.color.outline_variant_obsidian))
            }
            background = bg
            setPadding(32, 20, 32, 20)
            textSize = 14f
            setTextIsSelectable(true)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 32
                topMargin = 12
                bottomMargin = 12
                gravity = android.view.Gravity.START
            }
            layoutParams = params
        }
        layChatMessages.addView(bubble)
        scrollToBottom()
    }

    private fun addLoadingBubble(): View {
        val bubble = TextView(activity).apply {
            text = "🤖 Pensando respuesta con IA..."
            setTextColor(ContextCompat.getColor(context, R.color.cyber_teal))
            val bg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.obsidian_container_low))
                cornerRadius = 16f
            }
            background = bg
            setPadding(32, 20, 32, 20)
            textSize = 13f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 64
                topMargin = 12
                bottomMargin = 12
                gravity = android.view.Gravity.START
            }
            layoutParams = params
        }
        layChatMessages.addView(bubble)
        scrollToBottom()
        return bubble
    }

    private fun scrollToBottom() {
        scrollChat.post {
            scrollChat.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
