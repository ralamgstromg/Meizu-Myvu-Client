package com.myvu.client.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.myvu.client.R
import java.util.LinkedList

class WaveformVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cyber_teal)
        style = Paint.Style.FILL
    }

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline_variant_obsidian)
        style = Paint.Style.FILL
    }

    private val barRect = RectF()
    private val amplitudes = LinkedList<Float>()
    private var maxBars = 40
    private val barWidth = 8f
    private val barGap = 6f
    private val cornerRadius = 4f

    fun addAmplitude(rawAmplitude: Int) {
        val normalized = (rawAmplitude.toFloat() / 32767f).coerceIn(0.05f, 1.0f)
        if (amplitudes.size >= maxBars) {
            amplitudes.removeFirst()
        }
        amplitudes.add(normalized)
        invalidate()
    }

    fun clearWaveform() {
        amplitudes.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val availableWidth = w - paddingLeft - paddingRight
        maxBars = (availableWidth / (barWidth + barGap)).toInt().coerceAtLeast(10)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val maxHeight = (height - paddingTop - paddingBottom) * 0.9f

        val totalAmplitudes = amplitudes.size
        var startX = width - paddingRight - (totalAmplitudes * (barWidth + barGap))

        if (totalAmplitudes == 0) {
            // Draw idle resting line
            val idleHeight = 4f
            val count = maxBars
            for (i in 0 until count) {
                val x = paddingLeft + i * (barWidth + barGap)
                barRect.set(x, centerY - idleHeight / 2f, x + barWidth, centerY + idleHeight / 2f)
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, idlePaint)
            }
            return
        }

        for (amp in amplitudes) {
            val barHeight = (amp * maxHeight).coerceAtLeast(6f)
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            barRect.set(startX, top, startX + barWidth, bottom)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
            startX += barWidth + barGap
        }
    }
}
