package com.calendarmods

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout

/** Local face presence, using the AOSP FaceDetector available since API 1.
 * Camera frames are sampled at 2 Hz on one worker, never stored or transmitted. */
@Suppress("DEPRECATION")
internal class FaceBrightnessController(
    private val activity: Activity,
    private val root: FrameLayout,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val gate = FacePresenceGate()
    private var preview: TextureView? = null
    private var session: CaptureSession? = null
    private var active = false
    private var originalBrightness = -1f
    private var lastFrame = 0L
    private var bright: Boolean? = null
    private var fade: ValueAnimator? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrame > 12_000L) {
                fail("摄像头没有提供可用画面，已恢复原亮度。")
                return
            }
            setBright(gate.update(false, now))
            main.postDelayed(this, 500)
        }
    }

    fun start() {
        if (active) return
        if (!hasCamera()) { onError("此设备没有可用摄像头。"); return }
        active = true
        originalBrightness = activity.window.attributes.screenBrightness
        lastFrame = SystemClock.elapsedRealtime()
        gate.reset(lastFrame)
        setBright(true)
        main.postDelayed(tick, 500)
        // TextureView consumes preview buffers on API 14+, unlike an unattached SurfaceTexture.
        // It is under the opaque calendar and occupies one pixel; no preview is shown.
        preview = TextureView(activity).also { view ->
            view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    if (!active) return
                    val rotation = when (activity.windowManager.defaultDisplay.rotation) {
                        Surface.ROTATION_90 -> 90
                        Surface.ROTATION_180 -> 180
                        Surface.ROTATION_270 -> 270
                        else -> 0
                    }
                    session = CaptureSession(texture, rotation).also { it.open() }
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    session?.close(); session = null
                    return true
                }
            }
            root.addView(view, 0, FrameLayout.LayoutParams(1, 1))
        }
    }

    fun stop() {
        if (!active) return
        active = false
        main.removeCallbacks(tick)
        fade?.cancel(); fade = null
        session?.close(); session = null
        preview?.let { it.surfaceTextureListener = null; root.removeView(it) }
        preview = null
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
        bright = null
    }

    private fun fail(message: String) {
        if (!active) return
        stop()
        onError(message)
    }

    private fun setBright(value: Boolean) {
        if (bright == value) return
        bright = value
        val to = if (value) .85f else .02f
        val assigned = activity.window.attributes.screenBrightness
        val from = if (assigned >= 0f) assigned else try {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        } catch (_: SecurityException) { .5f }
        fade?.cancel()
        if (!MotionSettings.enabled(activity)) { setBrightness(to); return }
        fade = ValueAnimator.ofFloat(from, to).apply {
            duration = if (value) 240 else 900
            addUpdateListener { if (active) setBrightness(it.animatedValue as Float) }
            start()
        }
    }

    private fun setBrightness(value: Float) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
    }

    private inner class CaptureSession(private val texture: SurfaceTexture, private val displayRotation: Int) {
        private val thread = HandlerThread("calendar-face-presence").apply { start() }
        private val worker = Handler(thread.looper)
        @Volatile private var closed = false
        private var camera: Camera? = null
        private var detector: LuminanceFaceDetector? = null
        private var nextFrame = 0L
        private var rotation = 0
        private var width = 0
        private var height = 0

        fun open() { worker.post {
            if (closed) return@post
            try {
                val ids = (0 until Camera.getNumberOfCameras()).toList()
                val info = Camera.CameraInfo()
                val id = ids.firstOrNull {
                    Camera.getCameraInfo(it, info)
                    info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                } ?: ids.first()
                Camera.getCameraInfo(id, info)
                rotation = if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    (info.orientation + displayRotation) % 360 else (info.orientation - displayRotation + 360) % 360
                val opened = Camera.open(id)
                camera = opened
                val parameters = opened.parameters
                check(ImageFormat.NV21 in parameters.supportedPreviewFormats) { "NV21 unavailable" }
                val sizes = parameters.supportedPreviewSizes
                val size = sizes.filter { it.width <= 640 && it.height <= 480 }
                    .minByOrNull { kotlin.math.abs(it.width * it.height - 320 * 240) }
                    ?: sizes.minBy { it.width.toLong() * it.height }
                width = size.width; height = size.height
                parameters.setPreviewSize(width, height)
                parameters.previewFormat = ImageFormat.NV21
                parameters.supportedPreviewFpsRange?.filter { it[0] <= 15_000 && it[1] >= 15_000 }
                    ?.minByOrNull { it[1] }?.let { parameters.setPreviewFpsRange(it[0], it[1]) }
                opened.parameters = parameters
                detector = LuminanceFaceDetector(width, height, rotation)
                opened.setPreviewTexture(texture)
                opened.setErrorCallback { _, _ -> reportFailure() }
                opened.setPreviewCallbackWithBuffer { bytes, source ->
                    if (closed) return@setPreviewCallbackWithBuffer
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now >= nextFrame && bytes != null) {
                            nextFrame = now + 500L
                            val present = checkNotNull(detector).detect(bytes)
                            main.post {
                                if (active && session === this && !closed) {
                                    lastFrame = now
                                    setBright(gate.update(present, now))
                                }
                            }
                        }
                        if (!closed && bytes != null) source.addCallbackBuffer(bytes)
                    } catch (_: RuntimeException) { reportFailure() }
                      catch (_: LinkageError) { reportFailure() }
                }
                repeat(2) { opened.addCallbackBuffer(ByteArray(width * height * 3 / 2)) }
                opened.startPreview()
            } catch (_: Exception) { reportFailure() }
              catch (_: LinkageError) { reportFailure() }
        } }

        private fun reportFailure() {
            main.post {
                if (active && session === this) fail("摄像头或人脸检测暂不可用，已恢复原亮度。可退出全屏后重试。")
            }
        }

        fun close() {
            if (closed) return
            closed = true
            worker.post {
                try { camera?.setPreviewCallbackWithBuffer(null); camera?.stopPreview() } catch (_: RuntimeException) { }
                try { camera?.release() } catch (_: RuntimeException) { }
                camera = null
                detector?.close(); detector = null
                thread.quit()
            }
        }
    }

    companion object {
        fun hasCamera(): Boolean = try { Camera.getNumberOfCameras() > 0 } catch (_: RuntimeException) { false }
    }
}
