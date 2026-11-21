package com.calendarmods

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * The idle screen: the wall clock and the date, white on black, set as large as the display
 * allows. Any touch hands the page back; the host decides when it appears.
 */
internal class ScreenSaverView(context: Context) : View(context) {
    init {
        // Legacy HWUI/font atlases can drop individual display-sized glyphs (notably on
        // Android 5 Yoga tablets). Rasterise this minute-updated view into a software layer;
        // keep the calendar and its animations hardware accelerated.
        if (Build.VERSION.SDK_INT <= 22) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Follows the page's reading, so the idle clock does not switch format under the user. */
    var hour24: Boolean = true

    var onDismiss: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digits = Rect()
    private val colon = Rect()
    private val minute = Rect()
    private val mark = Rect()
    private val ticker = Handler(Looper.getMainLooper())
    // View.isAttachedToWindow is API 19; the compat branch keeps its own flag.
    private var attached = false

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            scheduleTick()
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawClock(canvas, clockFace(hour24))
        scheduleTick()
    }

    internal fun drawClock(canvas: Canvas, face: ClockFace,
        date: ScreenSaver.DateLine = ScreenSaver.dateLine(SystemCalendarClock.now().localDate())) {
        canvas.drawColor(Color.BLACK)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE

        // Reference sizes first; one fit factor then scales the whole block to the display. The
        // width uses the same three faces/runs as drawing; the colon is measured separately so
        // it can be lifted without changing the numerals' ink height.
        measure(face.hour + face.minute, REFERENCE, Plate.numerals, digits)
        val hourWidth = advance(face.hour, REFERENCE, Plate.numerals)
        val colonWidth = measure(COLON, REFERENCE, Plate.supporting, colon)
        val minuteWidth = measure(face.minute, REFERENCE, Plate.numerals, minute)
        val readingWidth = hourWidth + colonWidth + minuteWidth
        val period = face.period
        val markWidth = if (period.isEmpty()) {
            0f
        } else {
            measure(period, REFERENCE * MARK, Plate.supporting, mark)
        }
        val gap = REFERENCE * GAP
        val line = dateRuns(date)

        val blockWidth = max(readingWidth, markWidth).coerceAtLeast(1f)
        val blockHeight = (if (period.isEmpty()) 0f else mark.height() + gap) +
            digits.height() + gap + line.height
        val fit = min(width / blockWidth, height / blockHeight.coerceAtLeast(1f)) * FILL

        // The block — mark, reading, date — is centred; the mark sits on the reading's left shoulder.
        val left = (width - blockWidth * fit) / 2f
        var top = (height - blockHeight * fit) / 2f
        if (period.isNotEmpty()) {
            // Ink to ink: the digit's left bearing is not part of what the eye reads as the edge.
            paint.textSize = REFERENCE * MARK * fit
            paint.typeface = Plate.supporting
            canvas.drawText(period, left + (digits.left - mark.left) * fit, top - mark.top * fit, paint)
            top += (mark.height() + gap) * fit
        }
        // The colon is a mark, not a numeral: lift it until its ink shares the digits' centre line.
        val lift = ((digits.top + digits.bottom) - (colon.top + colon.bottom)) / 2f
        val baseline = top - digits.top * fit
        paint.textSize = REFERENCE * fit
        paint.typeface = Plate.numerals
        canvas.drawText(face.hour, left, baseline, paint)
        paint.typeface = Plate.supporting
        canvas.drawText(COLON, left + hourWidth * fit, baseline + lift * fit, paint)
        paint.typeface = Plate.numerals
        canvas.drawText(face.minute, left + (hourWidth + colonWidth) * fit, baseline, paint)
        // The date prints under the reading, sharing its ink's two edges.
        drawDate(canvas, line, left, left + (hourWidth + colonWidth + minute.right) * fit,
            top + digits.height() * fit + gap * fit, fit)
    }

    /**
     * The date line: 11月21日 六. Its numerals start on the ink the reading starts on and the
     * weekday ends on the ink the reading ends on, with 月 / 日 small between them.
     */
    private fun drawDate(canvas: Canvas, line: DateRuns, left: Float, readingRight: Float,
        top: Float, fit: Float) {
        val baseline = top - line.top * fit
        fun run(text: String, size: Float, x: Float) {
            paint.typeface = Plate.numerals
            paint.textSize = size * fit
            canvas.drawText(text, x, baseline, paint)
        }
        var cursor = left + (digits.left - line.start) * fit
        run(line.date.month, line.size, cursor)
        cursor += (line.month + line.gap) * fit
        run(MONTH, line.unitSize, cursor)
        cursor += (line.monthUnit + line.gap) * fit
        run(line.date.day, line.size, cursor)
        cursor += (line.day + line.gap) * fit
        run(DAY, line.unitSize, cursor)
        run(line.date.weekday, line.size, readingRight - line.weekRight * fit)
    }

    /** The date line measured at its own reference size, before the fit factor scales it. */
    private class DateRuns(
        val date: ScreenSaver.DateLine,
        val size: Float,
        val unitSize: Float,
        val month: Float,
        val monthUnit: Float,
        val day: Float,
        val dayUnit: Float,
        val gap: Float,
        /** Ink left of the month numerals and ink right of the weekday, the line's two anchors. */
        val start: Float,
        val weekRight: Float,
        val top: Float,
        val height: Float,
    )

    /**
     * Runs of the date line: the numbers and the weekday at one size, 月 / 日 smaller so they read
     * as marks. All ten digits and the seven weekday characters are measured for the ink box the
     * line is placed by, so the block keeps one height whatever day the screen lands on.
     */
    private fun dateRuns(date: ScreenSaver.DateLine): DateRuns {
        val size = REFERENCE * DATE
        val unitSize = size * DATE_MINOR
        val monthInk = Rect()
        val month = measure(date.month, size, Plate.numerals, monthInk)
        val weekInk = Rect()
        measure(date.weekday, size, Plate.numerals, weekInk)
        val digitInk = Rect()
        val weekdaysInk = Rect()
        measure(DIGITS, size, Plate.numerals, digitInk)
        measure(WEEKDAYS, size, Plate.numerals, weekdaysInk)
        val top = min(digitInk.top, weekdaysInk.top).toFloat()
        return DateRuns(date, size, unitSize,
            month = month,
            monthUnit = advance(MONTH, unitSize, Plate.numerals),
            day = advance(date.day, size, Plate.numerals),
            dayUnit = advance(DAY, unitSize, Plate.numerals),
            gap = size * DATE_GAP,
            start = monthInk.left.toFloat(),
            weekRight = weekInk.right.toFloat(),
            top = top,
            height = max(digitInk.bottom, weekdaysInk.bottom) - top,
        )
    }

    /** Sets [face] at [size] and returns the advance of [text]; the ink box lands in [box]. */
    private fun measure(text: String, size: Float, face: Typeface, box: Rect): Float {
        paint.typeface = face
        paint.textSize = size
        paint.getTextBounds(text, 0, text.length, box)
        return paint.measureText(text)
    }

    /** Advance of [text] alone, for placing a run without needing its ink. */
    private fun advance(text: String, size: Float, face: Typeface): Float {
        paint.typeface = face
        paint.textSize = size
        return paint.measureText(text)
    }

    /** The reading changes on the minute; nothing before then can alter the screen. */
    private fun scheduleTick() {
        if (!attached || windowVisibility != VISIBLE) return
        ticker.removeCallbacks(tick)
        val untilMinute = 60_000L - System.currentTimeMillis() % 60_000L
        ticker.postDelayed(tick, untilMinute.coerceAtLeast(16L))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            performClick()
            onDismiss?.invoke()
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
        ticker.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            invalidate()
            scheduleTick()
        } else ticker.removeCallbacks(tick)
    }

    private companion object {
        const val REFERENCE = 100f

        const val COLON = ":"

        /** The 上午 / 下午 mark prints at half the reading's size. */
        const val MARK = .5f

        /** Clear space between the mark and the reading, as a share of the reading's size. */
        const val GAP = .10f

        /** The date line prints at this share of the reading's size, its 月 / 日 at a share of that. */
        const val DATE = .34f
        const val DATE_MINOR = .72f

        /** Clear space between the date's runs, as a share of the date's size. */
        const val DATE_GAP = .10f

        const val MONTH = "月"
        const val DAY = "日"
        const val DIGITS = "0123456789"
        const val WEEKDAYS = "一二三四五六日"

        /** A hair of margin, so the widest reading never touches the display edge. */
        const val FILL = .98f
    }
}
