package com.calendarmods

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.util.Log
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
    private var lastRecovery = 0L
    private var awaitingFrames = false
    private var recoveryNotified = false
    private var bright: Boolean? = null
    private var fade: ValueAnimator? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (now - maxOf(lastFrame, lastRecovery) > 12_000L) {
                val capture = session
                lastRecovery = now
                if (capture == null) {
                    showRecovering()
                    preview?.let { root.removeView(it) }
                    attachPreview()
                } else capture.recover(IllegalStateException("Preview frame timeout"))
            }
            session?.updateRotation(displayRotation())
            if (!awaitingFrames) setBright(gate.update(false, now))
            main.postDelayed(this, 500)
        }
    }

    fun start() {
        if (active) return
        // The switch is only offered on devices that report a camera, so this stays a quiet no-op.
        if (!hasCamera()) return
        active = true
        originalBrightness = activity.window.attributes.screenBrightness
        lastFrame = SystemClock.elapsedRealtime()
        lastRecovery = 0L
        awaitingFrames = false
        recoveryNotified = false
        gate.reset(lastFrame)
        setBright(true)
        main.postDelayed(tick, 500)
        attachPreview()
    }

    private fun attachPreview() {
        // TextureView consumes preview buffers on API 14+, unlike an unattached SurfaceTexture.
        // It is under the opaque calendar and occupies one pixel; no preview is shown.
        preview = TextureView(activity).also { view ->
            var surfaceSession: CaptureSession? = null
            view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    if (!active) return
                    surfaceSession = CaptureSession(texture, displayRotation()).also {
                        session = it
                        it.open()
                    }
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    surfaceSession?.updateRotation(displayRotation())
                }
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    val owned = surfaceSession
                    if (session === owned) session = null
                    surfaceSession = null
                    if (owned == null) return true
                    // The camera producer must stop before its texture is released by the consumer.
                    owned.close(releaseTexture = true)
                    return false
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
        // Keep the listener until destruction; it releases the texture after the camera worker.
        preview?.let { root.removeView(it) }
        preview = null
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
        bright = null
    }

    private fun displayRotation(): Int = when (activity.windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun showRecovering() {
        if (!active || awaitingFrames) return
        awaitingFrames = true
        fade?.cancel(); fade = null
        bright = null
        setBrightness(originalBrightness)
        if (!recoveryNotified) {
            recoveryNotified = true
            onError("人脸检测暂时中断，已恢复原亮度，正在自动重连。")
        }
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

    private inner class CaptureSession(private val texture: SurfaceTexture, initialRotation: Int) {
        // Shared across Activity instances: recreation cannot open a camera while its old owner
        // is still releasing it. All camera, detector and texture operations use this queue.
        private val worker = FaceCameraWorker.handler
        @Volatile private var closed = false
        private var camera: Camera? = null
        private var detector: LuminanceFaceDetector? = null
        private var nextFrame = 0L
        private var displayRotation = initialRotation
        private var requestedRotation = initialRotation
        private var cameraInfo: FaceCameraInfo? = null
        private var width = 0
        private var height = 0
        private val retry = FaceRecoveryBackoff()
        private val reopen = Runnable { openAttempt() }
        private var recovering = false
        private var receivedFrame = false
        private var textureReleased = false
        private val failedCandidates = mutableSetOf<Int>()

        fun open() { worker.post { openAttempt() } }

        private fun openAttempt() {
            if (closed) return
            recovering = false
            receivedFrame = false
            var failure: Throwable = IllegalStateException("No accessible camera")
            val ordered = FaceCameraPolicy.ordered(cameras())
            val candidates = ordered.filter { it.id !in failedCandidates }.ifEmpty {
                failedCandidates.clear(); ordered
            }
            for (info in candidates) {
                if (closed) return
                try {
                    val opened = Camera.open(info.id)
                    camera = opened
                    cameraInfo = info
                    val parameters = opened.parameters
                    val sizes = parameters.supportedPreviewSizes.orEmpty()
                    val size = sizes.filter { it.width <= 640 && it.height <= 480 }
                        .minByOrNull { kotlin.math.abs(it.width * it.height - 320 * 240) }
                        ?: sizes.minByOrNull { it.width.toLong() * it.height }
                    if (size != null) parameters.setPreviewSize(size.width, size.height)
                    parameters.previewFormat = ImageFormat.NV21
                    try { opened.parameters = parameters }
                    catch (_: RuntimeException) {
                        // Some older HALs advertise combinations they reject. Retain their default
                        // resolution/rate and request only the format needed by the detector.
                        val defaults = opened.parameters
                        defaults.previewFormat = ImageFormat.NV21
                        opened.parameters = defaults
                    }
                    val actual = opened.parameters
                    check(actual.previewFormat == ImageFormat.NV21) { "NV21 unavailable" }
                    width = actual.previewSize.width; height = actual.previewSize.height
                    detector = LuminanceFaceDetector(width, height, FaceCameraPolicy.rotation(info, displayRotation))
                    opened.setPreviewTexture(texture)
                    opened.setErrorCallback { code, source ->
                        if (source === camera && !closed) recover(IllegalStateException("Camera error $code"))
                    }
                    opened.setPreviewCallbackWithBuffer { bytes, source ->
                        if (closed || source !== camera) return@setPreviewCallbackWithBuffer
                        try {
                            val now = SystemClock.elapsedRealtime()
                            if (now >= nextFrame && bytes != null) {
                                nextFrame = now + 500L
                                val present = checkNotNull(detector).detect(bytes)
                                receivedFrame = true
                                retry.onFrame(now)
                                main.post {
                                    if (active && session === this && !closed) {
                                        lastFrame = now
                                        if (awaitingFrames) {
                                            awaitingFrames = false
                                            gate.reset(now)
                                        }
                                        setBright(gate.update(present, now))
                                    }
                                }
                            }
                            if (!closed && source === camera && bytes != null) source.addCallbackBuffer(bytes)
                        } catch (error: RuntimeException) { recover(error) }
                          catch (error: LinkageError) { recover(error) }
                    }
                    repeat(2) { opened.addCallbackBuffer(ByteArray(width * height * 3 / 2)) }
                    opened.startPreview()
                    Log.i("FaceBrightness", "Camera ${info.id}, front=${info.front}, sensor=${info.orientation}, display=$displayRotation, ${width}x$height")
                    return
                } catch (error: Exception) { failure = error }
                  catch (error: LinkageError) { failure = error }
                releaseCamera()
            }
            recover(failure)
        }

        fun updateRotation(value: Int) {
            if (requestedRotation == value || closed) return
            requestedRotation = value
            worker.post {
                if (closed) return@post
                displayRotation = value
                val info = cameraInfo ?: return@post
                if (camera == null) return@post
                try {
                    detector?.close()
                    detector = LuminanceFaceDetector(width, height, FaceCameraPolicy.rotation(info, value))
                } catch (error: RuntimeException) { recover(error) }
                  catch (error: LinkageError) { recover(error) }
            }
        }

        fun recover(error: Throwable) { worker.post {
            if (closed || recovering) return@post
            recovering = true
            val delay = retry.nextDelayMillis()
            Log.w("FaceBrightness", "Preview interrupted; retry in ${delay}ms", error)
            main.post { if (active && session === this && !closed) showRecovering() }
            // A previously working front camera gets first chance after a transient stall.
            // Only candidates that cannot produce frames fall through to another camera.
            if (receivedFrame) failedCandidates.clear()
            else cameraInfo?.let { failedCandidates.add(it.id) }
            releaseCamera()
            // Keep recovering for as long as this fullscreen session is active. Backoff is
            // capped, and resets only after a stable stream, so repeated HAL faults cannot spin.
            worker.removeCallbacks(reopen)
            worker.postDelayed(reopen, delay)
        } }

        private fun releaseCamera() {
            try { camera?.setPreviewCallbackWithBuffer(null); camera?.stopPreview() } catch (_: RuntimeException) { }
            try { camera?.setErrorCallback(null) } catch (_: RuntimeException) { }
            try { camera?.release() } catch (_: RuntimeException) { }
            camera = null
            detector?.close(); detector = null
        }

        fun close(releaseTexture: Boolean = false) {
            closed = true
            worker.removeCallbacks(reopen)
            worker.post {
                releaseCamera()
                if (releaseTexture && !textureReleased) {
                    textureReleased = true
                    texture.release()
                }
            }
        }
    }

    companion object {
        private fun cameras(): List<FaceCameraInfo> = try {
            (0 until Camera.getNumberOfCameras()).mapNotNull { id ->
                try {
                    val info = Camera.CameraInfo()
                    Camera.getCameraInfo(id, info)
                    FaceCameraInfo(id, info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT, info.orientation)
                } catch (_: RuntimeException) { null }
            }
        } catch (_: RuntimeException) { emptyList() }
        fun hasCamera(): Boolean = cameras().isNotEmpty()
    }
}
