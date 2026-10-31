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
 * Theme chooser: a horizontally paged strip of live previews plus the store entry.
 * Replaces the Compose pager; snap, fling and the upward delete gesture stay the same.
 */
internal class ChooserView(context: Context) : View(context) {
    var themes: List<CalendarTheme> = emptyList()
    var data: CalendarData? = null
    var styles: Map<String, ThemeStyle> = emptyMap()
    var environment: AlmanacEnvironment = AlmanacEnvironment()
    var inventory: ThemeInventory = ThemeInventory()

    var onSelect: ((CalendarTheme) -> Unit)? = null
    var onStyle: ((CalendarTheme) -> Unit)? = null
    var onStore: (() -> Unit)? = null
    var onDelete: ((CalendarTheme) -> Unit)? = null
    var onPageChanged: ((CalendarTheme?) -> Unit)? = null

    private val scroller = Scroller(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pending = mutableSetOf<String>()
    private var velocity: VelocityTracker? = null

    private var page = 0
    private var lastPage = 0
    private var dragging = false
    private var verticalDrag = false
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragY = 0f
    private var armedDelete = false
    private var startScroll = 0

    private val styleButton = RectF()

    private fun dp(value: Float) = value * density

    private val headerHeight get() = dp(44f)
    private val footerHeight get() = dp(132f)

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
            lastPage = index
            scrollX = index * width
        }
        invalidate()
    }

    fun selectPage(index: Int, animate: Boolean) {
        val target = index.coerceIn(0, themes.size)
        if (!animate) {
            page = target
            lastPage = target
            scrollTo(target * width, 0)
            invalidate()
            return
        }
        val dx = (target * width - scrollX)
        scroller.startScroll(scrollX, 0, dx, 0, 280)
        page = target
        invalidate()
    }

    private fun previewRect(): RectF {
        val areaTop = headerHeight
        val areaBottom = height - footerHeight
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
        scrollTo(page * w, 0)
    }

    override fun onDraw(canvas: Canvas) {
        val themes = themes
        if (themes.isEmpty()) return
        val index = (scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt().coerceIn(0, themes.size)
        val theme = themes.getOrNull(index)
        canvas.drawColor(theme?.surfaces?.backdrop?.toInt() ?: 0xFFECEEE9.toInt())

        val rect = previewRect()
        canvas.save()
        if (verticalDrag) canvas.translate(0f, dragY)
        for (i in 0..themes.size) {
            val left = (i * width - scrollX).toFloat()
            if (left > width || left < -width) continue
            if (i == themes.size) drawStoreEntry(canvas, left, rect)
            else drawPreview(canvas, themes[i], left, rect)
        }
        canvas.restore()

        val shop = index == themes.size
        val foreground = if (shop) 0xFF20231E.toInt() else (theme?.surfaces?.chrome?.toInt() ?: 0xFF51554F.toInt())
        drawChrome(canvas, foreground, index, shop, theme, rect.top)
        if (verticalDrag) drawDeleteHint(canvas, rect, foreground)
    }

    private fun drawChrome(canvas: Canvas, foreground: Int, index: Int, shop: Boolean,
        theme: CalendarTheme?, previewTop: Float) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = foreground
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText("CALENDARMODS", dp(24f), dp(28f), paint)
        val counter = if (shop) "主题商店" else "${(index + 1).toString().padStart(2, '0')} / ${themes.size.toString().padStart(2, '0')}"
        canvas.drawText(counter, width - dp(24f) - paint.measureText(counter), dp(28f), paint)

        val footerTop = height - footerHeight
        val title = if (shop) "主题商店" else theme?.title.orEmpty()
        paint.textSize = dp(13f)
        canvas.drawText(title, dp(24f), footerTop + dp(30f), paint)

        // Style button, 48 dp circle at the right edge of the footer title row.
        styleButton.set(width - dp(24f) - dp(48f), footerTop + dp(6f), width - dp(24f), footerTop + dp(54f))
        if (!shop) {
            paint.color = Plate.withAlpha(foreground, .10f)
            canvas.drawCircle(styleButton.centerX(), styleButton.centerY(), dp(24f), paint)
            paint.color = foreground
            paint.textSize = dp(17f)
            paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
            val label = "Aa"
            canvas.drawText(label, styleButton.centerX() - paint.measureText(label) / 2f,
                styleButton.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)
        }

        // Page dots around the current page.
        val total = themes.size + 1
        val start = (index - 4).coerceIn(0, (total - 9).coerceAtLeast(0))
        val dotY = footerTop + dp(72f)
        val count = (min(start + 9, total) - start)
        val dotWidths = FloatArray(count) { if (start + it == index) dp(16f) else dp(4f) }
        val gap = dp(6f)
        val totalWidth = dotWidths.sum() + gap * (count - 1)
        var x = (width - totalWidth) / 2f
        paint.color = foreground
        for (n in 0 until count) {
            val active = start + n == index
            paint.alpha = ((if (active) .85f else .25f) * 255).toInt()
            canvas.drawRoundRect(RectF(x, dotY, x + dotWidths[n], dotY + dp(4f)), dp(2f), dp(2f), paint)
            x += dotWidths[n] + gap
        }
        paint.alpha = 255

        paint.color = Plate.withAlpha(foreground, .8f)
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        val hint = if (shop) "发现喜欢的日历 · 管理你的主题" else "左右滑动切换 · 单击全屏 · 上滑删除"
        canvas.drawText(hint, (width - paint.measureText(hint)) / 2f, dotY + dp(26f), paint)
    }

    private fun drawPreview(canvas: Canvas, theme: CalendarTheme, offset: Float, rect: RectF) {
        val left = rect.left + offset
        val data = data ?: return
        val style = styles[theme.id] ?: theme.defaultStyle()
        val w = rect.width().toInt()
        val h = rect.height().toInt()
        val key = PreviewCache.key(theme, data, style, environment, w, h)
        val bitmap = PreviewCache.get(key)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, RectF(left, rect.top, left + rect.width(), rect.bottom), null)
        } else {
            paint.reset(); paint.isAntiAlias = true; paint.color = theme.surfaces.paper.toInt()
            canvas.drawRect(left, rect.top, left + rect.width(), rect.bottom, paint)
            requestPreview(key, theme, data, style, w, h)
        }
    }

    private fun requestPreview(key: String, theme: CalendarTheme, data: CalendarData, style: ThemeStyle,
        w: Int, h: Int) {
        if (!pending.add(key)) return
        Ui.io {
            PreviewCache.render(context, key, theme, data, style, environment, w, h, inventory)
            Ui.post {
                pending.remove(key)
                invalidate()
            }
        }
    }

    private fun drawStoreEntry(canvas: Canvas, offset: Float, rect: RectF) {
        val left = rect.left + offset
        paint.reset(); paint.isAntiAlias = true
        paint.color = 0xFFF2F1EC.toInt()
        canvas.drawRoundRect(RectF(left, rect.top, left + rect.width(), rect.bottom), dp(12f), dp(12f), paint)
        paint.color = 0xFF51554F.toInt()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        val cx = left + rect.width() / 2f
        val cy = rect.top + rect.height() / 2f
        canvas.drawLine(cx - dp(14f), cy, cx + dp(14f), cy, paint)
        canvas.drawLine(cx, cy - dp(14f), cx, cy + dp(14f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawDeleteHint(canvas: Canvas, rect: RectF, foreground: Int) {
        val alpha = ((-dragY) / dp(120f)).coerceIn(0f, 1f)
        paint.reset(); paint.isAntiAlias = true
        paint.color = 0xFFB3261E.toInt(); paint.alpha = (alpha * 255).toInt()
        val cx = width / 2f
        val cy = rect.bottom + dp(28f)
        canvas.drawCircle(cx, cy, dp(22f), paint)
        paint.color = 0xFFFFFFFF.toInt(); paint.textSize = dp(22f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText("×", cx - paint.measureText("×") / 2f, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            val newPage = (scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt()
            if (newPage != page) {
                page = newPage.coerceIn(0, themes.size)
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
                dragging = false; verticalDrag = false; dragY = 0f; armedDelete = false
                startScroll = scrollX
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && !verticalDrag) {
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx)) verticalDrag = true
                    else if (abs(dx) > touchSlop) dragging = true
                }
                if (verticalDrag) {
                    dragY = min(0f, dy)
                    armedDelete = -dragY > dp(96f)
                    invalidate()
                } else if (dragging) {
                    scrollTo((startScroll + (downX - event.x)).toInt()
                        .coerceIn(0, themes.size * width), 0)
                    val newPage = (scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt()
                    if (newPage != page) {
                        page = newPage.coerceIn(0, themes.size)
                        onPageChanged?.invoke(themes.getOrNull(page))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val elapsed = SystemClock.uptimeMillis() - downAt
                when {
                    verticalDrag -> {
                        val target = themes.getOrNull(page)
                        verticalDrag = false
                        val delete = armedDelete && target != null
                        dragY = 0f; armedDelete = false
                        invalidate()
                        if (delete) onDelete?.invoke(target!!)
                    }
                    dragging -> {
                        dragging = false
                        vc.computeCurrentVelocity(1000)
                        val vx = vc.xVelocity
                        var target = (scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt()
                        if (abs(vx) > 400f) target += if (vx < 0) 1 else -1
                        target = target.coerceIn(0, themes.size)
                        page = target
                        lastPage = target
                        scroller.startScroll(scrollX, 0, target * width - scrollX, 0, 300)
                        onPageChanged?.invoke(themes.getOrNull(target))
                        invalidate()
                    }
                    else -> handleTap(event.x, event.y)
                }
                vc.recycle(); velocity = null
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val index = (scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt().coerceIn(0, themes.size)
        if (index == themes.size) { onStore?.invoke(); return }
        val theme = themes.getOrNull(index) ?: return
        if (!styleButton.isEmpty() && styleButton.contains(x, y)) { onStyle?.invoke(theme); return }
        val rect = previewRect()
        if (rect.contains(x, y)) onSelect?.invoke(theme)
    }

    /** Keeps the strip aligned if the view is recreated after rotation. */
    fun restorePage(index: Int) {
        page = index
        lastPage = index
        scrollTo(index * width, 0)
    }
}
