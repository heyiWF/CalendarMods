package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

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
    var environment: AlmanacEnvironment = AlmanacEnvironment()
    var inventory: ThemeInventory = ThemeInventory()

    var onLongPress: (() -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private var image: Bitmap? = null
    private var imagePending = false
    private var font: Typeface? = null
    private var outgoing: Bitmap? = null
    private var animating = false
    private var animStarted = 0L
    private var animDuration = 760

    private val icons by lazy { AlmanacIcons(context) }
    private val artwork by lazy { PeitaiyiArtwork(context) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val camera = Camera()
    private val matrix = Matrix()

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPress?.invoke()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY) * 1.5f && kotlin.math.abs(velocityX) > 320f) {
                onSwipe?.invoke(if (velocityX < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    fun setPage(theme: CalendarTheme, data: CalendarData, style: ThemeStyle,
        environment: AlmanacEnvironment, inventory: ThemeInventory, animate: Boolean) {
        val previousData = this.data
        val previousTheme = this.theme
        val changed = previousData != null && previousData.today != data.today
        if (animate && changed && previousTheme?.id == theme.id && width > 0 && height > 0) {
            snapshotCurrent()
        } else {
            recycleOutgoing()
        }
        this.theme = theme
        this.data = data
        this.style = style
        this.environment = environment
        this.inventory = inventory
        animDuration = theme.renderer.dayMotion.millis
        image = null
        font = Ui.themeFont(theme, style, inventory)
        requestImage()
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
        animStarted = System.currentTimeMillis()
        scheduleFrame()
    }

    private fun recycleOutgoing() {
        animating = false
        outgoing?.recycle()
        outgoing = null
    }

    private fun requestImage() {
        val theme = theme ?: return
        if (theme.renderer.image == null) return
        val edge = ThemeImages.targetEdge(width, height)
        val sources = imageSources(theme)
        ThemeImages.peek(sources, edge)?.let { image = it; return }
        if (imagePending) return
        imagePending = true
        Ui.io {
            val bitmap = ThemeImages.load(context.resources, sources, edge) ?: return@io
            Ui.post {
                imagePending = false
                if (this@PaperView.theme?.id == theme.id) {
                    image = bitmap
                    invalidate()
                }
            }
        }
    }

    private fun imageSources(theme: CalendarTheme): List<ThemeImageSource> {
        val packaged = inventory.installed.firstOrNull { it.theme.id == theme.id }?.imageFile
            ?.let { java.io.File(context.filesDir, "themes-v1/$it").absolutePath }
        val builtIn = Ui.builtInImage(theme.renderer) ?: return emptyList()
        return listOfNotNull(style.imagePath, packaged).map { ThemeImageSource(path = it) } +
            ThemeImageSource(resource = builtIn)
    }

    private fun drawPage(canvas: Canvas, theme: CalendarTheme, data: CalendarData) {
        val w = width.toFloat()
        val h = height.toFloat()
        val wide = w > h
        val artwork = PageArtwork(theme, data, style, environment, wide, w, h,
            if (theme.renderer.image == null) null else image,
            font, icons,
            if (theme.renderer == ThemeRenderer.PEITAIYI) artwork else null)
        canvas.drawColor(artwork.paperColor())
        val unit = if (wide) minOf(w / 640f, h / 360f) else minOf(w / 360f, h / 600f)
        canvas.save()
        canvas.scale(unit, unit)
        artwork.draw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val theme = theme ?: return
        val data = data ?: return
        if (image == null && theme.renderer.image != null) requestImage()
        drawPage(canvas, theme, data)
        if (animating && outgoing != null) drawOutgoing(canvas)
    }

    private fun drawOutgoing(canvas: Canvas) {
        val snapshot = outgoing ?: return
        val elapsed = (System.currentTimeMillis() - animStarted).toFloat() / animDuration
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        recycleOutgoing()
        super.onDetachedFromWindow()
    }
}
