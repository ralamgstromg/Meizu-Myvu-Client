package com.myvu.client.core

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView

object MarkdownRenderer {

    /**
     * Parses standard Markdown formatting into a rich Android SpannableStringBuilder.
     * Supports:
     * - Headers (# H1, ## H2, ### H3)
     * - Bold (**text** or __text__)
     * - Italic (*text* or _text_)
     * - Inline code (`code`) and multi-line code blocks (```code```)
     * - Bullet points (- item, * item, • item)
     * - Blockquotes (> quote)
     */
    fun render(markdown: String): CharSequence {
        if (markdown.isBlank()) return ""

        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val builder = SpannableStringBuilder()

        var inCodeBlock = false
        val codeBlockContent = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i]

            // Multi-line code block handling
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    val start = builder.length
                    builder.append(codeBlockContent.toString().trimEnd())
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(BackgroundColorSpan(Color.parseColor("#1F2428")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(ForegroundColorSpan(Color.parseColor("#00F0FF")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    builder.append("\n\n")
                    codeBlockContent.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n")
                continue
            }

            val trimmed = line.trim()

            // Header 1 (# Title)
            if (trimmed.startsWith("# ")) {
                val text = trimmed.substring(2).trim()
                val start = builder.length
                appendInlineFormatted(builder, text)
                val end = builder.length
                builder.setSpan(RelativeSizeSpan(1.35f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#00F0FF")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("\n\n")
                continue
            }

            // Header 2 (## Title)
            if (trimmed.startsWith("## ")) {
                val text = trimmed.substring(3).trim()
                val start = builder.length
                appendInlineFormatted(builder, text)
                val end = builder.length
                builder.setSpan(RelativeSizeSpan(1.2f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#38BDF8")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("\n\n")
                continue
            }

            // Header 3 (### Title)
            if (trimmed.startsWith("### ")) {
                val text = trimmed.substring(4).trim()
                val start = builder.length
                appendInlineFormatted(builder, text)
                val end = builder.length
                builder.setSpan(RelativeSizeSpan(1.1f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#E0E7FF")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("\n\n")
                continue
            }

            // Blockquote (> Quote)
            if (trimmed.startsWith("> ")) {
                val text = trimmed.substring(2).trim()
                val start = builder.length
                builder.append("▌ ")
                appendInlineFormatted(builder, text)
                val end = builder.length
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#94A3B8")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("\n\n")
                continue
            }

            // Bullet items (- item, * item, • item)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                val text = trimmed.substring(2).trim()
                val start = builder.length
                builder.append("  •  ")
                appendInlineFormatted(builder, text)
                val end = builder.length
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#E2E8F0")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("\n")
                continue
            }

            // Numbered items (1. item, 2. item)
            val numberedMatch = Regex("^([0-9]+)\\.\\s+(.*)").find(trimmed)
            if (numberedMatch != null) {
                val num = numberedMatch.groupValues[1]
                val text = numberedMatch.groupValues[2]
                val start = builder.length
                builder.append("  $num. ")
                appendInlineFormatted(builder, text)
                val end = builder.length
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#E2E8F0")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append("\n")
                continue
            }

            // Empty line
            if (trimmed.isEmpty()) {
                builder.append("\n")
                continue
            }

            // Normal paragraph with inline formatting
            appendInlineFormatted(builder, line)
            builder.append("\n")
        }

        // Remove trailing double newlines
        return builder.trimEnd()
    }

    private fun appendInlineFormatted(builder: SpannableStringBuilder, text: String) {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Bold **text** or __text__
            if ((text.startsWith("**", cursor) || text.startsWith("__", cursor)) && cursor + 2 < length) {
                val marker = text.substring(cursor, cursor + 2)
                val closing = text.indexOf(marker, cursor + 2)
                if (closing != -1) {
                    val content = text.substring(cursor + 2, closing)
                    val start = builder.length
                    builder.append(content)
                    val end = builder.length
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(Color.parseColor("#FFFFFF")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    cursor = closing + 2
                    continue
                }
            }

            // Inline code `code`
            if (text.startsWith("`", cursor) && cursor + 1 < length) {
                val closing = text.indexOf("`", cursor + 1)
                if (closing != -1) {
                    val content = text.substring(cursor + 1, closing)
                    val start = builder.length
                    builder.append(" $content ")
                    val end = builder.length
                    builder.setSpan(TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(BackgroundColorSpan(Color.parseColor("#242B33")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(Color.parseColor("#00F0FF")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    cursor = closing + 1
                    continue
                }
            }

            // Italic *text* or _text_
            if ((text.startsWith("*", cursor) || text.startsWith("_", cursor)) && cursor + 1 < length) {
                val marker = text.substring(cursor, cursor + 1)
                val closing = text.indexOf(marker, cursor + 1)
                if (closing != -1 && closing > cursor + 1) {
                    val content = text.substring(cursor + 1, closing)
                    val start = builder.length
                    builder.append(content)
                    val end = builder.length
                    builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    cursor = closing + 1
                    continue
                }
            }

            // Regular char
            builder.append(text[cursor])
            cursor++
        }
    }
}

/**
 * Extension helper for easy TextView markdown rendering.
 */
fun TextView.setMarkdown(markdown: String?) {
    text = if (markdown.isNullOrBlank()) "" else MarkdownRenderer.render(markdown)
}
