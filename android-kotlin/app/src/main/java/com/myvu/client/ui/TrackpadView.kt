package com.myvu.client.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.myvu.client.app.feature.Trackpad

class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onTap()
        fun onDoubleTap()
        fun onLongPress()
        fun onSwipe(
            direction: Int,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            speedX: Float,
            speedY: Float
        )
    }

    private var listener: Listener? = null
    private lateinit var detector: GestureDetector

    private var scrolled: Boolean = false
    private var beginX: Float = 0f
    private var beginY: Float = 0f
    private var endX: Float = 0f
    private var endY: Float = 0f
    private var beginTime: Long = 0

    private var swipeThreshold: Float = 0f
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dotGap: Float = 0f
    private var dotRadius: Float = 0f

    init {
        initView()
    }

    fun setListener(l: Listener?) {
        this.listener = l
    }

    private fun initView() {
        val d = resources.displayMetrics.density
        swipeThreshold = 64 * d
        dotGap = 26 * d
        dotRadius = 1.6f * d
        dotPaint.color = Color.parseColor("#33FFFFFF")

        detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener?.onTap()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                listener?.onDoubleTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                listener?.onLongPress()
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                scrolled = true
                endX = e2.x
                endY = e2.y
                return true
            }
        })
        detector.setIsLongpressEnabled(true)
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginX = event.x
                beginY = event.y
                endX = beginX
                endY = beginY
                beginTime = System.currentTimeMillis()
                scrolled = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                emitSwipeIfAny()
                return true
            }
            else -> return true
        }
    }

    private fun emitSwipeIfAny() {
        val l = listener ?: return
        if (!scrolled) return
        val dx = endX - beginX
        val dy = endY - beginY
        if (Math.abs(dx) < swipeThreshold && Math.abs(dy) < swipeThreshold) return
        val dur = Math.max(1L, System.currentTimeMillis() - beginTime)
        val direction = if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) Trackpad.SWIPE_RIGHT else Trackpad.SWIPE_LEFT
        } else {
            if (dy > 0) Trackpad.SWIPE_DOWN else Trackpad.SWIPE_UP
        }
        l.onSwipe(direction, beginX, beginY, endX, endY, dx / dur, dy / dur)
        scrolled = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var y = dotGap
        while (y < height) {
            var x = dotGap
            while (x < width) {
                canvas.drawCircle(x, y, dotRadius, dotPaint)
                x += dotGap
            }
            y += dotGap
        }
    }
}
