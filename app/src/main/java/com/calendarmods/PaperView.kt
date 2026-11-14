package com.calendarmods

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * One full-window calendar page. The midnight change turns the printed page away from the
 * viewer; framework Canvas has no retained layers, so the outgoing page is snapshotted once.
 *
 * A horizontal drag on the month grid turns the same sheet around its side edge instead, and it
 * follows the finger: the page underneath is the neighbouring month drawn live, so a frame costs
 * one page render whatever the fold angle.
 */
internal class PaperView(context: Context) : View(context) {
    var theme: CalendarTheme? = null
        private set
    var data: CalendarData? = null
        private set
    var style: ThemeStyle = ThemeStyle()
        private set
    var dark: Boolean = false
        private set

    var onLongPress: (() -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    /** A date printed in the shown month was tapped. */
    var onPickDate: ((PlainDate) -> Unit)? = null

    /** A month turn was asked for without the sheet following the finger (data was not ready). */
    var onMonthStep: ((Int) -> Unit)? = null

    /** The sheet finished turning onto another month. */
    var onMonthSettled: ((PlainDate) -> Unit)? = null

    /** The page a month turn would reveal, or null while the host is still preparing it. */
    var monthPage: ((Int) -> CalendarData?)? = null

    /** A direct drag supersedes any asynchronous navigation request held by the host. */
    var onMonthTurnStarted: (() -> Unit)? = null

    private var face: ClockFace = clockFace(true)
    private var outgoing: Bitmap? = null
    private var animating = false
    private var animStarted = 0L

    // The sheet that is turning away and the month it reveals.
    private var turned: CalendarData? = null
    private var turnDirection = 0
    private var turnProgress = 0f
    private var sheet: Bitmap? = null
    private var turnAnimator: ValueAnimator? = null
    private var turning = false

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
    private var velocity: VelocityTracker? = null

    // The month grid as it was last printed, in view pixels.
    private var gridLeft = 0f
    private var gridTop = 0f
    private var gridCellW = 0f
    private var gridCellH = 0f
    private var gridHeaderH = 0f
    private var gridPrinted = false

    // The clock ticks on the minute and the month grid slides every few seconds; both only need a
    // redraw when the visible content can actually change.
    private val ticker = Handler(Looper.getMainLooper())
    private var attached = false
    private var labelDelay = Long.MAX_VALUE
    // The uptimeMillis the last frame was drawn at; scheduling from the same clock as the draw
    // keeps the settle frame from being skipped when a slow draw straddles a transition wrap.
    private var frameNow = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!attached) return
            face = clockFace(style.hour24)
            invalidate()
        }
    }

    fun setPage(theme: CalendarTheme, data: CalendarData, style: ThemeStyle, animate: Boolean,
        dark: Boolean) {
        val previousData = this.data
        val changed = previousData != null && previousData.today != data.today
        // A new page identity makes any turn in flight obsolete.
        if (turned != null) cancelTurn()
        if (animate && MotionSettings.enabled(context) && changed && width > 0 && height > 0) {
            snapshotCurrent()
        } else if (changed || this.style != style || this.dark != dark) {
            recycleOutgoing()
        }
        this.theme = theme
        this.data = data
        this.style = style
        this.dark = dark
        gridPrinted = false
        face = clockFace(style.hour24)
        invalidate()
    }

    /**
     * Turns the sheet onto [target] without a drag, e.g. after a date was picked from the grey
     * edge of the grid. [direction] is +1 when the next month comes in from the right.
     */
    fun flipTo(target: CalendarData, direction: Int, animate: Boolean) {
        if (target.today == data?.today) return
        if (!animate || !MotionSettings.enabled(context) || width <= 0 || height <= 0) {
            settle(target)
            return
        }
        if (!beginTurn(target, direction)) {
            settle(target)
            return
        }
        turnProgress = 0f
        settleTurn(commit = true)
    }

    /** Jumps straight to [target], for a turn that cannot be drawn or must not be animated. */
    private fun settle(target: CalendarData) {
        recycleOutgoing()
        turnAnimator?.cancel()
        finishTurn(commit = true, target)
    }

    private fun snapshotCurrent() {
        val theme = theme ?: return
        val data = data ?: return
        val snapshot = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return
        drawPage(Canvas(snapshot), theme, data, record = false)
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

    private fun drawPage(canvas: Canvas, theme: CalendarTheme, data: CalendarData, record: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val wide = w > h
        // The page is laid out in design units; the canvas supplies the pixel scale.
        val unit = if (wide) minOf(w / 640f, h / 360f) else minOf(w / 360f, h / 600f)
        val now = SystemClock.uptimeMillis()
        frameNow = now
        val page = DailyPage(theme.scheme(dark), data, face, wide, w / unit, h / unit,
            MotionSettings.enabled(context), now, style.fontScale)
        canvas.save()
        canvas.scale(unit, unit)
        page.draw(canvas)
        canvas.restore()
        labelDelay = min(labelDelay, page.labelDelay)
        if (record) recordGrid(page.gridSpan, unit)
    }

    /** Keeps the printed grid in view pixels so taps and drags can be mapped back onto cells. */
    private fun recordGrid(span: DailyPage.GridSpan?, unit: Float) {
        gridPrinted = span != null
        if (span == null) return
        gridLeft = span.left * unit
        gridTop = span.top * unit
        gridCellW = span.cellW * unit
        gridCellH = span.cellH * unit
        gridHeaderH = span.headerH * unit
    }

    override fun onDraw(canvas: Canvas) {
        val theme = theme ?: return
        val data = data ?: return
        val next = turned
        labelDelay = Long.MAX_VALUE
        if (next != null && sheet != null) {
            // The month underneath is drawn live; only the sheet that is turning is a snapshot.
            drawPage(canvas, theme, next, record = false)
            drawTurningSheet(canvas)
        } else {
            drawPage(canvas, theme, data, record = true)
            if (animating && outgoing != null) drawOutgoing(canvas)
        }
        // The frame that was just printed knows when the page can look different again, so the
        // tick is scheduled from here rather than before the draw.
        scheduleTick()
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

    /**
     * The page turning away: hinged on the edge the finger pushes from, folded by the angle whose
     * free edge sits under the finger. The camera sits [CAMERA_PAGES] page widths back so the far
     * edge of a full-width sheet never reaches the projection plane.
     */
    private fun drawTurningSheet(canvas: Canvas) {
        val snapshot = sheet ?: return
        val p = turnProgress
        val forward = turnDirection > 0
        val hinge = if (forward) 0f else width.toFloat()
        // The pivot carries the projection centre as well: keeping it halfway down the page stops
        // the lower half from being dragged towards the top corner as the sheet recedes.
        val middle = height / 2f
        matrix.reset()
        camera.save()
        // Camera locations are read in inches, 72 points to the inch.
        camera.setLocation(0f, 0f, -CAMERA_PAGES * width / 72f)
        camera.rotateY((if (forward) 1f else -1f) * MonthFlip.angle(p, 1f / CAMERA_PAGES))
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-hinge, -middle)
        matrix.postTranslate(hinge, middle)
        val checkpoint = canvas.save()
        canvas.concat(matrix)
        paint.alpha = (1f - ((p - .76f) / .24f).coerceIn(0f, 1f)).times(255).toInt()
        canvas.drawBitmap(snapshot, 0f, 0f, paint)
        // A sheet turning away from the light falls into shadow; without it the fold reads as a
        // horizontal squeeze rather than a page leaving the stack.
        paint.color = 0xFF000000.toInt()
        paint.alpha = (255 * .13f * p).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restoreToCount(checkpoint)
        paint.alpha = 255
        val shade = Math.sin(p * Math.PI).toFloat().coerceAtLeast(0f)
        if (shade > 0f) {
            // The free edge of the sheet cuts a soft shadow across the page underneath.
            val edge = if (forward) width * (1f - p) else width * p
            paint.color = 0xFF000000.toInt()
            paint.alpha = (0x2E * shade).toInt()
            canvas.drawRect(edge - 1f, 0f, edge + 1f, height.toFloat(), paint)
        }
    }

    // ------------------------------------------------------------------ month turn

    /** Folds the page that is on screen away from [direction]'s edge, revealing [target]. */
    private fun beginTurn(target: CalendarData, direction: Int): Boolean {
        val theme = theme ?: return false
        val data = data ?: return false
        if (width <= 0 || height <= 0) return false
        val snapshot = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return false
        recycleOutgoing()
        recycleSheet()
        drawPage(Canvas(snapshot), theme, data, record = false)
        sheet = snapshot
        turned = target
        turnDirection = direction
        turnProgress = 0f
        return true
    }

    /** Drops the sheet and returns to the page that is on screen. */
    private fun cancelTurn() {
        turnAnimator?.cancel()
        turnAnimator = null
        turning = false
        turned = null
        turnProgress = 0f
        recycleSheet()
    }

    private fun recycleSheet() {
        sheet?.recycle()
        sheet = null
    }

    /** Animates the rest of a released turn, then either lands on the new month or unfolds. */
    private fun settleTurn(commit: Boolean) {
        val target = turned ?: return
        val end = if (commit) 1f else 0f
        val motion = MotionSettings.enabled(context) && style.pageAnimation
        if (!motion || turnProgress == end) {
            finishTurn(commit, target)
            return
        }
        turnAnimator?.cancel()
        turnAnimator = ValueAnimator.ofFloat(turnProgress, end).apply {
            duration = MonthFlip.settleMillis(turnProgress, end)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                turnProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) finishTurn(commit, target)
                }
            })
            start()
        }
    }

    private fun finishTurn(commit: Boolean, target: CalendarData) {
        turnAnimator = null
        turning = false
        turned = null
        turnProgress = 0f
        recycleSheet()
        // The printed grid belongs to the page that was just replaced; the next frame re-records
        // it, so a tap arriving in between cannot be mapped onto the wrong month.
        gridPrinted = false
        if (commit) {
            data = target
            face = clockFace(style.hour24)
            invalidate()
            onMonthSettled?.invoke(target.today)
        } else {
            invalidate()
        }
    }

    /** Ends a running settle where the finger landed, so a new touch never fights the animation. */
    private fun snapTurn() {
        val target = turned ?: return
        val commit = turnProgress >= .5f
        turnAnimator?.cancel()
        finishTurn(commit, target)
    }

    /** View.postInvalidateOnAnimation is API 16; a 60 Hz delay keeps Android 4.0 in step. */
    private fun scheduleFrame() {
        if (android.os.Build.VERSION.SDK_INT >= 16) postInvalidateOnAnimation() else postInvalidateDelayed(16)
    }

    // ------------------------------------------------------------------ touch

    private var touchActive = false
    private var touchMoved = false
    private var downInGrid = false
    private var gridDecided = false

    /** Whether [x], [y] sit on the printed month grid — including its weekday header. */
    private fun inGrid(x: Float, y: Float): Boolean = gridPrinted &&
        x >= gridLeft && x <= gridLeft + gridCellW * 7f &&
        y >= gridTop && y <= gridTop + gridHeaderH + gridCellH * 6f

    /** The date printed under [x], [y], whichever month it belongs to. */
    private fun cellAt(x: Float, y: Float): PlainDate? {
        val cells = data?.cells ?: return null
        if (!gridPrinted) return null
        // Float.toInt truncates toward zero: without these bounds the weekday header and
        // positions just outside the left/top edge incorrectly select the first row/column.
        if (x < gridLeft || x >= gridLeft + gridCellW * 7f ||
            y < gridTop + gridHeaderH || y >= gridTop + gridHeaderH + gridCellH * 6f) return null
        val column = ((x - gridLeft) / gridCellW).toInt()
        val row = ((y - gridTop - gridHeaderH) / gridCellH).toInt()
        if (column !in 0..6 || row !in 0..5) return null
        return cells.getOrNull(row * 7 + column)?.date
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (turnAnimator != null) snapTurn()
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                touchActive = true
                touchMoved = false
                gridDecided = false
                downInGrid = style.interactive && inGrid(event.x, event.y)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (turning) {
                    followTurn(event.x - downX)
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) touchMoved = true
                if (!gridDecided && touchActive && touchMoved && downInGrid && abs(dx) > abs(dy)) {
                    gridDecided = true
                    startTurnDrag(dx)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                touchActive = false
                if (turning) {
                    turning = false
                    settleTurn(commit = false)
                }
            }
            MotionEvent.ACTION_UP -> onUp(event)
        }
        return true
    }

    private fun onUp(event: MotionEvent) {
        if (turning) {
            turning = false
            touchActive = false
            // The finger's last position counts too: a release slightly past the last move still
            // decides where the sheet was left.
            followTurn(event.x - downX)
            velocity?.addMovement(event)
            velocity?.computeCurrentVelocity(1000)
            val x = velocity?.xVelocity ?: 0f
            // Positive velocity opens the turn, whichever edge the sheet is hinged on.
            val opening = if (turnDirection > 0) -x else x
            settleTurn(commit = MonthFlip.commits(turnProgress, opening))
            return
        }
        if (!touchActive) return
        touchActive = false
        val dx = event.x - downX
        val dy = event.y - downY
        val moved = touchMoved || abs(dx) > touchSlop || abs(dy) > touchSlop
        val elapsed = SystemClock.uptimeMillis() - downAt
        val horizontal = abs(dx) > touchSlop * 2 && abs(dx) > abs(dy) * 1.4f
        when {
            !moved && elapsed >= longPressMillis -> onLongPress?.invoke()
            // A tap on the grid picks the date printed there; the host decides whether that is a
            // day change or a month change.
            !moved && downInGrid -> {
                performClick()
                cellAt(event.x, event.y)?.let { onPickDate?.invoke(it) }
            }
            // The gesture belonged to the grid, so it never steps the day by one.
            downInGrid && horizontal -> onMonthStep?.invoke(if (dx < 0) 1 else -1)
            horizontal -> onSwipe?.invoke(if (dx < 0) 1 else -1)
            !moved -> { performClick(); onTap?.invoke() }
        }
    }

    /** Starts the sheet following the finger, when the neighbouring page is already prepared. */
    private fun startTurnDrag(dx: Float) {
        if (!style.pageAnimation || !MotionSettings.enabled(context)) return
        val direction = if (dx < 0) 1 else -1
        val target = monthPage?.invoke(direction) ?: return
        val current = data?.today ?: return
        if (CalendarPaging.monthStep(current, direction) != target.today) return
        if (!beginTurn(target, direction)) return
        turning = true
        onMonthTurnStarted?.invoke()
        followTurn(dx)
    }

    private fun followTurn(dx: Float) {
        val distance = if (turnDirection > 0) -dx else dx
        turnProgress = MonthFlip.progress(distance, width.toFloat())
        invalidate()
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
        cancelTurn()
        velocity?.recycle()
        velocity = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) scheduleTick() else stopTicker()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!attached) return
        if (isShown) {
            face = clockFace(style.hour24)
            invalidate()
            scheduleTick()
        } else stopTicker()
    }

    private fun stopTicker() {
        ticker.removeCallbacks(tick)
    }

    /**
     * Wake on the next clock minute, the next label transition or the next step of a walking
     * label, whichever comes first.
     */
    private fun scheduleTick() {
        if (!attached || windowVisibility != VISIBLE || !isShown) return
        ticker.removeCallbacks(tick)
        // The clock follows the wall clock, the label slide follows the monotonic timer.
        val untilMinute = 60_000L - System.currentTimeMillis() % 60_000L
        // frameDelay reads the drawn frame's timestamp, not a fresh one: a fresh read can wrap a
        // transition and delay the next redraw by seconds, freezing the incoming label low.
        val frame = if (MotionSettings.enabled(context)) {
            minOf(DailyPage.frameDelay(frameNow), labelDelay)
        } else Long.MAX_VALUE
        ticker.postDelayed(tick, minOf(untilMinute, frame).coerceAtLeast(16L))
    }

    private companion object {
        const val ANIMATION_MS = 760f

        /**
         * Camera distance in page widths: close enough that the free edge of the sheet visibly
         * recedes, far enough that it never reaches the projection plane.
         */
        const val CAMERA_PAGES = 1.6f
    }
}
