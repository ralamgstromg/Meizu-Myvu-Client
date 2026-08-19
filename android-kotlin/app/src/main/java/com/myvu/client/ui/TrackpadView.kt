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
    private var isTouching: Boolean = false
    private var beginX: Float = 0f
    private var beginY: Float = 0f
    private var endX: Float = 0f
    private var endY: Float = 0f
    private var currentTouchX: Float = 0f
    private var currentTouchY: Float = 0f
    private var beginTime: Long = 0

    private var swipeThreshold: Float = 0f
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touchGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touchRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touchCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dotGap: Float = 0f
    private var dotRadius: Float = 0f
    private var density: Float = 1f

    init {
        initView()
    }

    fun setListener(l: Listener?) {
        this.listener = l
    }

    private fun initView() {
        val d = resources.displayMetrics.density
        density = d
        swipeThreshold = 64 * d
        dotGap = 26 * d
        dotRadius = 1.6f * d
        dotPaint.color = Color.parseColor("#33FFFFFF")

        touchGlowPaint.apply {
            color = Color.parseColor("#2600F0FF")
            style = Paint.Style.FILL
        }
        touchRingPaint.apply {
            color = Color.parseColor("#8000F0FF")
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
        }
        touchCenterPaint.apply {
            color = Color.parseColor("#E600F0FF")
            style = Paint.Style.FILL
        }
        trailPaint.apply {
            color = Color.parseColor("#5500F0FF")
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
            strokeCap = Paint.Cap.ROUND
        }

        detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

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
                currentTouchX = e2.x
                currentTouchY = e2.y
                invalidate()
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
                currentTouchX = beginX
                currentTouchY = beginY
                beginTime = System.currentTimeMillis()
                scrolled = false
                isTouching = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentTouchX = event.x
                currentTouchY = event.y
                endX = event.x
                endY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isTouching = false
                emitSwipeIfAny()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                scrolled = false
                invalidate()
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

        if (isTouching) {
            val d = density
            if (scrolled) {
                canvas.drawLine(beginX, beginY, currentTouchX, currentTouchY, trailPaint)
            }
            val touchRadius = 26f * d
            canvas.drawCircle(currentTouchX, currentTouchY, touchRadius, touchGlowPaint)
            canvas.drawCircle(currentTouchX, currentTouchY, touchRadius, touchRingPaint)
            canvas.drawCircle(currentTouchX, currentTouchY, 3.5f * d, touchCenterPaint)
        }
    }
}
