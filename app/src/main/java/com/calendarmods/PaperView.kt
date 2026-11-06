package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * One full-window calendar page. The midnight change turns the printed page away from the
 * viewer; framework Canvas has no retained layers, so the outgoing page is snapshotted once.
 */
internal class PaperView(context: Context) : View(context) {
    var theme: CalendarTheme? = null
        private set
    var data: CalendarData? = null
        private set
    var style: ThemeStyle = ThemeStyle()
        private set

    var onLongPress: (() -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private var time = clockText()
    private var outgoing: Bitmap? = null
    private var animating = false
    private var animStarted = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val camera = Camera()
    private val matrix = Matrix()

    // Touch is tracked by hand: GestureDetector reports a long press before it reports the fling
    // when a swipe is delivered slowly, which used to drop the page while browsing dates.
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMillis = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    // The clock ticks on the minute and the month grid slides every few seconds; both only need a
    // redraw when the visible content can actually change.
    private val ticker = Handler(Looper.getMainLooper())
    private var attached = false
    private val tick = object : Runnable {
        override fun run() {
            if (!attached) return
            time = clockText()
            invalidate()
            scheduleTick()
        }
    }

    fun setPage(theme: CalendarTheme, data: CalendarData, style: ThemeStyle, animate: Boolean) {
        val previousData = this.data
        val changed = previousData != null && previousData.today != data.today
        if (animate && MotionSettings.enabled(context) && changed && width > 0 && height > 0) {
            snapshotCurrent()
        } else if (changed || this.style != style) {
            recycleOutgoing()
        }
        this.theme = theme
        this.data = data
        this.style = style
        time = clockText()
        invalidate()
    }

    private fun snapshotCurrent() {
        val theme = theme ?: return
        val data = data ?: return
        val snapshot = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return
        drawPage(Canvas(snapshot), theme, data)
        recycleOutgoing()
        outgoing = snapshot
        animating = true
        animStarted = SystemClock.uptimeMillis()
        scheduleFrame()
    }

    private fun recycleOutgoing() {
        animating = false
        outgoing?.recycle()
        outgoing = null
    }

    private fun drawPage(canvas: Canvas, theme: CalendarTheme, data: CalendarData) {
        val w = width.toFloat()
        val h = height.toFloat()
        val wide = w > h
        // The page is laid out in design units; the canvas supplies the pixel scale.
        val unit = if (wide) minOf(w / 640f, h / 360f) else minOf(w / 360f, h / 600f)
        val page = DailyPage(theme, data, time, wide, w / unit, h / unit,
            MotionSettings.enabled(context), SystemClock.uptimeMillis(), style.fontScale)
        canvas.save()
        canvas.scale(unit, unit)
        page.draw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val theme = theme ?: return
        val data = data ?: return
        drawPage(canvas, theme, data)
        if (animating && outgoing != null) drawOutgoing(canvas)
    }

    private fun drawOutgoing(canvas: Canvas) {
        val snapshot = outgoing ?: return
        val elapsed = (SystemClock.uptimeMillis() - animStarted).toFloat() / ANIMATION_MS
        val p = DecelerateInterpolator(1.6f).getInterpolation(elapsed.coerceIn(0f, 1f))
        val shade = Math.sin(p * Math.PI).toFloat().coerceAtLeast(0f)
        matrix.reset()
        camera.save()
        camera.rotateX(-89.5f * p)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-width / 2f, 0f)
        matrix.postTranslate(width / 2f, 0f)
        val checkpoint = canvas.save()
        canvas.concat(matrix)
        paint.alpha = (1f - ((p - .76f) / .24f).coerceIn(0f, 1f)).times(255).toInt()
        canvas.drawBitmap(snapshot, 0f, 0f, paint)
        canvas.restoreToCount(checkpoint)
        paint.alpha = 255
        if (shade > 0f) {
            // A soft shadow sells the lift without a second render pass.
            paint.color = 0xFF000000.toInt()
            paint.alpha = (0x33 * shade).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), 2f, paint)
        }
        if (elapsed >= 1f) recycleOutgoing() else scheduleFrame()
    }

    /** View.postInvalidateOnAnimation is API 16; a 60 Hz delay keeps Android 4.0 in step. */
    private fun scheduleFrame() {
        if (android.os.Build.VERSION.SDK_INT >= 16) postInvalidateOnAnimation() else postInvalidateDelayed(16)
    }

    private var touchActive = false
    private var touchMoved = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downAt = SystemClock.uptimeMillis()
                touchActive = true; touchMoved = false
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                touchMoved = true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> touchActive = false
            MotionEvent.ACTION_UP -> if (touchActive) {
                touchActive = false
                val dx = event.x - downX
                val dy = event.y - downY
                val moved = touchMoved || abs(dx) > touchSlop || abs(dy) > touchSlop
                when {
                    abs(dx) > touchSlop * 2 && abs(dx) > abs(dy) * 1.4f ->
                        onSwipe?.invoke(if (dx < 0) 1 else -1)
                    !moved && SystemClock.uptimeMillis() - downAt >= longPressMillis -> onLongPress?.invoke()
                    !moved -> { performClick(); onTap?.invoke() }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        scheduleTick()
    }

    override fun onDetachedFromWindow() {
        attached = false
        stopTicker()
        recycleOutgoing()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) scheduleTick() else stopTicker()
    }

    private fun stopTicker() {
        ticker.removeCallbacks(tick)
    }

    /** Wake on the next clock minute or on the next label transition, whichever comes first. */
    private fun scheduleTick() {
        if (!attached || windowVisibility != VISIBLE) return
        ticker.removeCallbacks(tick)
        // The clock follows the wall clock, the label slide follows the monotonic timer.
        val untilMinute = 60_000L - System.currentTimeMillis() % 60_000L
        val frame = DailyPage.frameDelay(SystemClock.uptimeMillis())
        ticker.postDelayed(tick, minOf(untilMinute, frame).coerceAtLeast(16L))
    }

    private companion object {
        const val ANIMATION_MS = 760f
    }
}
