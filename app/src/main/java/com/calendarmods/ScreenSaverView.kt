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
 * The idle screen: the wall clock alone, white on black, set as large as the display allows. Any
 * touch hands the page back; the host decides when it appears.
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

    internal fun drawClock(canvas: Canvas, face: ClockFace) {
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
        val readingWidth = hourWidth + colonWidth + advance(face.minute, REFERENCE, Plate.numerals)
        val period = face.period
        val markWidth = if (period.isEmpty()) {
            0f
        } else {
            measure(period, REFERENCE * MARK, Plate.supporting, mark)
        }
        val gap = REFERENCE * GAP
        val blockWidth = max(readingWidth, markWidth).coerceAtLeast(1f)
        val blockHeight = digits.height() + if (period.isEmpty()) 0f else gap + mark.height()
        val fit = min(width / blockWidth, height / blockHeight.coerceAtLeast(1f)) * FILL

        // The block — mark over reading — is centred; the mark sits on the reading's left shoulder.
        val left = (width - readingWidth * fit) / 2f
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

        /** A hair of margin, so the widest reading never touches the display edge. */
        const val FILL = .96f
    }
}
