package com.calendarmods

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Theme chooser: a horizontally paged strip of live previews.
 * Replaces the Compose pager; snap and fling behave the same.
 *
 * Paging keeps its own offset instead of calling View.scrollTo: a scrolled view draws with the
 * canvas translated by -scrollX, which would shift every page off screen on top of the offset
 * the strip already applies to each card.
 */
internal class ChooserView(context: Context) : View(context) {
    var themes: List<CalendarTheme> = emptyList()
    var data: CalendarData? = null
    var styles: Map<String, ThemeStyle> = emptyMap()
    var environment: AlmanacEnvironment = AlmanacEnvironment()
    var inventory: ThemeInventory = ThemeInventory()

    var onSelect: ((CalendarTheme) -> Unit)? = null
    var onStyle: ((CalendarTheme) -> Unit)? = null
    var onPageChanged: ((CalendarTheme?) -> Unit)? = null

    private val scroller = Scroller(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pending = mutableSetOf<String>()
    private var velocity: VelocityTracker? = null

    private var page = 0
    private var scroll = 0
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var startScroll = 0

    private val styleButton = RectF()
    private var insetTop = 0
    private var insetBottom = 0

    private fun dp(value: Float) = value * density

    private val headerHeight get() = insetTop + dp(44f)
    private val footerHeight get() = dp(132f)

    private val lastIndex get() = (themes.size - 1).coerceAtLeast(0)

    /** Android 15 draws edge to edge; older releases already exclude the system bars. */
    fun setWindowInsets(top: Int, bottom: Int) {
        if (top == insetTop && bottom == insetBottom) return
        insetTop = top
        insetBottom = bottom
        invalidate()
    }

    val currentTheme: CalendarTheme? get() = themes.getOrNull(page)

    fun setContent(themes: List<CalendarTheme>, data: CalendarData?, styles: Map<String, ThemeStyle>,
        environment: AlmanacEnvironment, inventory: ThemeInventory, selectedId: String?) {
        this.themes = themes
        this.data = data
        this.styles = styles
        this.environment = environment
        this.inventory = inventory
        val index = themes.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        if (page != index && !dragging) {
            page = index
            scroll = index * width
        }
        invalidate()
    }

    fun selectPage(index: Int, animate: Boolean) {
        val target = index.coerceIn(0, lastIndex)
        if (!animate) {
            page = target
            scroll = target * width
            invalidate()
            return
        }
        val dx = (target * width - scroll)
        scroller.startScroll(scroll, 0, dx, 0, 280)
        page = target
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // setContent runs before the first layout, so re-anchor the strip once widths are known.
        scroll = page * w
    }

    override fun onDraw(canvas: Canvas) {
        val themes = themes
        if (themes.isEmpty()) return
        val index = pageIndex().coerceIn(0, lastIndex)
        val theme = themes.getOrNull(index)
        canvas.drawColor(theme?.surfaces?.backdrop?.toInt() ?: 0xFFECEEE9.toInt())

        val rect = previewRect()
        themes.forEachIndexed { i, item ->
            val left = (i * width - scroll).toFloat()
            if (left <= width && left >= -width) drawPreview(canvas, item, left, rect)
        }

        val foreground = theme?.surfaces?.chrome?.toInt() ?: 0xFF51554F.toInt()
        drawChrome(canvas, foreground, index, theme, rect.top)
    }

    private fun pageIndex(): Int = (scroll.toFloat() / width.coerceAtLeast(1)).roundToInt()

    private fun drawChrome(canvas: Canvas, foreground: Int, index: Int,
        theme: CalendarTheme?, previewTop: Float) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = foreground
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText("CALENDARMODS", dp(24f), insetTop + dp(28f), paint)
        val counter = "${(index + 1).toString().padStart(2, '0')} / ${themes.size.toString().padStart(2, '0')}"
        canvas.drawText(counter, width - dp(24f) - paint.measureText(counter), insetTop + dp(28f), paint)

        val footerTop = height - insetBottom - footerHeight
        val title = theme?.title.orEmpty()
        val preview = previewRect()
        paint.textSize = dp(13f)
        canvas.drawText(title, preview.centerX() - paint.measureText(title) / 2f, footerTop + dp(30f), paint)

        // Style button, 48 dp circle at the right edge of the footer title row.
        styleButton.set(preview.right - dp(48f), footerTop + dp(6f), preview.right, footerTop + dp(54f))
        paint.color = Plate.withAlpha(foreground, .10f)
        canvas.drawCircle(styleButton.centerX(), styleButton.centerY(), dp(24f), paint)
        paint.color = foreground
        paint.textSize = dp(17f)
        paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
        val label = "Aa"
        canvas.drawText(label, styleButton.centerX() - paint.measureText(label) / 2f,
            styleButton.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)

        // One dot per theme, so the row always shows how many themes exist and the marker moves
        // on every page. The gap tightens if a future theme set would not fit.
        val total = themes.size
        val dotY = footerTop + dp(72f)
        val activeWidth = dp(16f)
        val idleWidth = dp(4f)
        val maxWidth = width - dp(48f)
        var gap = dp(6f)
        var natural = activeWidth + idleWidth * (total - 1) + gap * (total - 1)
        if (total > 1 && natural > maxWidth) {
            gap = ((maxWidth - activeWidth - idleWidth * (total - 1)) / (total - 1)).coerceAtLeast(dp(2f))
            natural = activeWidth + idleWidth * (total - 1) + gap * (total - 1)
        }
        var x = (width - natural) / 2f
        paint.color = foreground
        for (n in 0 until total) {
            val active = n == index
            val dotWidth = if (active) activeWidth else idleWidth
            paint.alpha = ((if (active) .85f else .25f) * 255).toInt()
            canvas.drawRoundRect(RectF(x, dotY, x + dotWidth, dotY + dp(4f)), dp(2f), dp(2f), paint)
            x += dotWidth + gap
        }
        paint.alpha = 255

        paint.color = Plate.withAlpha(foreground, .8f)
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        val hint = "左右滑动切换 · 单击全屏"
        canvas.drawText(hint, (width - paint.measureText(hint)) / 2f, dotY + dp(26f), paint)
    }

    private fun drawPreview(canvas: Canvas, theme: CalendarTheme, offset: Float, rect: RectF) {
        val left = rect.left + offset
        val data = data ?: return
        val style = styles[theme.id] ?: theme.defaultStyle()
        val w = rect.width().toInt()
        val h = rect.height().toInt()
        val scale = min(1f, PREVIEW_EDGE / maxOf(rect.width(), rect.height()))
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        val key = PreviewCache.key(theme, data, style, environment, bw, bh)
        val bitmap = PreviewCache.get(key)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, RectF(left, rect.top, left + rect.width(), rect.bottom), bitmapPaint)
        } else {
            paint.reset(); paint.isAntiAlias = true; paint.color = theme.surfaces.paper.toInt()
            canvas.drawRect(left, rect.top, left + rect.width(), rect.bottom, paint)
            requestPreview(key, theme, data, style, bw, bh)
        }
    }

    private fun requestPreview(key: String, theme: CalendarTheme, data: CalendarData, style: ThemeStyle,
        w: Int, h: Int) {
        if (!pending.add(key)) return
        val requestedEnvironment = environment
        val requestedInventory = inventory
        Ui.io {
            try {
                PreviewCache.render(context.applicationContext, key, theme, data, style, requestedEnvironment, w, h, requestedInventory)
            } finally {
                Ui.post { pending.remove(key); invalidate() }
            }
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scroll = scroller.currX
            val newPage = pageIndex().coerceIn(0, lastIndex)
            if (newPage != page) {
                page = newPage
                onPageChanged?.invoke(themes.getOrNull(page))
            }
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (themes.isEmpty()) return false
        val vc = velocity ?: VelocityTracker.obtain().also { velocity = it }
        vc.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                downX = event.x; downY = event.y; downAt = SystemClock.uptimeMillis()
                dragging = false
                startScroll = scroll
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) >= abs(dy)) dragging = true
                if (dragging) {
                    scroll = (startScroll + (downX - event.x)).toInt().coerceIn(0, lastIndex * width)
                    val newPage = pageIndex().coerceIn(0, lastIndex)
                    if (newPage != page) {
                        page = newPage
                        onPageChanged?.invoke(themes.getOrNull(page))
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when {
                    dragging -> {
                        dragging = false
                        vc.computeCurrentVelocity(1000)
                        val vx = vc.xVelocity
                        // A fling moves exactly one page from where the gesture started; letting the
                        // distance rounding and the velocity each add a page could skip two at once.
                        var target: Int
                        if (abs(vx) > 400f) {
                            target = (startScroll.toFloat() / width.coerceAtLeast(1)).roundToInt() +
                                if (vx < 0) 1 else -1
                        } else {
                            target = pageIndex()
                        }
                        target = target.coerceIn(0, lastIndex)
                        page = target
                        scroller.startScroll(scroll, 0, target * width - scroll, 0, 300)
                        onPageChanged?.invoke(themes.getOrNull(target))
                        invalidate()
                    }
                    else -> if (event.actionMasked == MotionEvent.ACTION_UP) handleTap(event.x, event.y)
                }
                vc.recycle(); velocity = null
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val theme = themes.getOrNull(pageIndex().coerceIn(0, lastIndex)) ?: return
        if (!styleButton.isEmpty() && styleButton.contains(x, y)) { onStyle?.invoke(theme); return }
        if (previewRect().contains(x, y)) onSelect?.invoke(theme)
    }

    /** Keeps the strip aligned if the view is recreated after rotation. */
    fun restorePage(index: Int) {
        page = index.coerceIn(0, lastIndex)
        scroll = page * width
    }

    private companion object {
        /**
         * Preview bitmaps are cached, and three full-size pages would not fit in the LRU budget
         * on a low-heap device. Capping the edge keeps every visible card resident.
         */
        const val PREVIEW_EDGE = 900f
    }
}
