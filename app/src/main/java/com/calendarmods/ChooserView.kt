package com.calendarmods

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.min

/**
 * Start screen: one live preview of the page, a style button and the tap hint. The preview is a
 * cached bitmap, so it never redraws the page per frame.
 */
internal class ChooserView(context: Context) : View(context) {
    var theme: CalendarTheme? = null
    var data: CalendarData? = null
    var style: ThemeStyle = ThemeStyle()
    var dark: Boolean = false
        private set

    var onSelect: (() -> Unit)? = null
    var onStyle: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pending = mutableSetOf<String>()

    private val styleButton = RectF()
    private var insetTop = 0
    private var insetBottom = 0

    private var downX = 0f
    private var downY = 0f

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            scheduleTick()
        }
    }

    private fun dp(value: Float) = value * density

    private val headerHeight get() = insetTop + dp(44f)
    private val footerHeight get() = dp(136f)

    /** Android 15 draws edge to edge; older releases already exclude the system bars. */
    fun setWindowInsets(top: Int, bottom: Int) {
        if (top == insetTop && bottom == insetBottom) return
        insetTop = top
        insetBottom = bottom
        invalidate()
    }

    fun setContent(theme: CalendarTheme?, data: CalendarData?, style: ThemeStyle, dark: Boolean) {
        this.theme = theme
        this.data = data
        this.style = style
        this.dark = dark
        invalidate()
    }

    private fun previewRect(): RectF {
        val areaTop = headerHeight
        val areaBottom = height - insetBottom - footerHeight
        val areaHeight = (areaBottom - areaTop).coerceAtLeast(1f)
        val ratio = width.toFloat() / height.coerceAtLeast(1)
        val previewW = min(width - dp(36f), areaHeight * ratio)
        val previewH = previewW / ratio
        val left = (width - previewW) / 2f
        val top = areaTop + (areaHeight - previewH) / 2f
        return RectF(left, top, left + previewW, top + previewH)
    }

    override fun onDraw(canvas: Canvas) {
        val theme = theme ?: return
        val data = data ?: return
        val scheme = theme.scheme(dark)
        canvas.drawColor(scheme.backdrop.toInt())
        val foreground = scheme.chrome.toInt()
        val rect = previewRect()

        // A soft drop shadow lifts the sheet off the backdrop without a second card.
        paint.reset(); paint.isAntiAlias = true
        paint.color = if (dark) 0x66000000 else 0x1F000000
        canvas.drawRect(rect.left + dp(2f), rect.top + dp(4f), rect.right + dp(2f), rect.bottom + dp(4f), paint)

        drawPreview(canvas, theme, data, rect)
        drawChrome(canvas, foreground, rect)
    }

    private fun drawChrome(canvas: Canvas, foreground: Int, preview: RectF) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = foreground
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText("CALENDARMODS", dp(24f), insetTop + dp(28f), paint)
        val stamp = clockFace(style.hour24).text
        canvas.drawText(stamp, width - dp(24f) - paint.measureText(stamp), insetTop + dp(28f), paint)

        val footerTop = height - insetBottom - footerHeight
        val title = theme?.title.orEmpty()
        paint.textSize = dp(13f)
        canvas.drawText(title, preview.centerX() - paint.measureText(title) / 2f, footerTop + dp(26f), paint)

        // Style button, 48 dp circle at the right edge of the footer title row.
        styleButton.set(preview.right - dp(48f), footerTop + dp(2f), preview.right, footerTop + dp(50f))
        paint.color = Plate.withAlpha(foreground, .10f)
        canvas.drawCircle(styleButton.centerX(), styleButton.centerY(), dp(24f), paint)
        paint.color = foreground
        paint.textSize = dp(17f)
        paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
        val label = "Aa"
        canvas.drawText(label, styleButton.centerX() - paint.measureText(label) / 2f,
            styleButton.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)

        paint.color = Plate.withAlpha(foreground, .8f)
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        val hint = "单击日历进入全屏 · 长按返回"
        canvas.drawText(hint, (width - paint.measureText(hint)) / 2f, height - insetBottom - dp(28f), paint)
    }

    private fun drawPreview(canvas: Canvas, theme: CalendarTheme, data: CalendarData, rect: RectF) {
        val w = rect.width().toInt()
        val h = rect.height().toInt()
        if (w <= 0 || h <= 0) return
        val scale = min(1f, PREVIEW_EDGE / maxOf(rect.width(), rect.height()))
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        val key = PreviewCache.key(theme, data, style, dark, bw, bh)
        val bitmap = PreviewCache.get(key)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        } else {
            paint.reset(); paint.isAntiAlias = true; paint.color = theme.scheme(dark).paper.toInt()
            canvas.drawRect(rect, paint)
            requestPreview(key, theme, data, bw, bh)
        }
    }

    private fun requestPreview(key: String, theme: CalendarTheme, data: CalendarData, w: Int, h: Int) {
        if (!pending.add(key)) return
        val requestedStyle = style
        val requestedDark = dark
        Ui.io {
            try {
                PreviewCache.render(context.applicationContext, key, theme, data, requestedStyle,
                    requestedDark, w, h)
            } finally {
                Ui.post { pending.remove(key); invalidate() }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                    performClick()
                    handleTap(event.x, event.y)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun handleTap(x: Float, y: Float) {
        if (!styleButton.isEmpty() && styleButton.contains(x, y)) { onStyle?.invoke(); return }
        if (previewRect().contains(x, y)) onSelect?.invoke()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleTick()
    }

    override fun onDetachedFromWindow() {
        ticker.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    /** The preview prints the clock, so it wakes once a minute even without a touch. */
    private fun scheduleTick() {
        ticker.removeCallbacks(tick)
        ticker.postDelayed(tick, 60_000L - System.currentTimeMillis() % 60_000L + 16L)
    }

    private companion object {
        /** Previews are cached; a smaller edge keeps the bitmap well inside the LRU budget. */
        const val PREVIEW_EDGE = 900f
    }
}
