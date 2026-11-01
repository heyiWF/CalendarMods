package com.calendarmods

import android.graphics.Bitmap
import android.media.FaceDetector

/** Reuses a small RGB_565 buffer; accepts only the Y plane of an NV21 camera frame. */
internal class LuminanceFaceDetector(private val width: Int, private val height: Int, private val rotation: Int) {
    private val sample = ((maxOf(width, height) + 319) / 320).coerceAtLeast(1)
    private val sw = width / sample
    private val sh = height / sample
    private val outputWidth = (if (rotation % 180 == 0) sw else sh) and -2
    private val outputHeight = if (rotation % 180 == 0) sh else sw
    private val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.RGB_565)
    private val detector = FaceDetector(outputWidth, outputHeight, 3)
    private val faces = arrayOfNulls<FaceDetector.Face>(3)
    private val pixels = IntArray(outputWidth * outputHeight)

    fun detect(bytes: ByteArray): Boolean {
        require(bytes.size >= width * height)
        for (y in 0 until outputHeight) for (x in 0 until outputWidth) {
            val sx: Int
            val sy: Int
            when (rotation) {
                90 -> { sx = y; sy = sh - 1 - x }
                180 -> { sx = sw - 1 - x; sy = sh - 1 - y }
                270 -> { sx = sw - 1 - y; sy = x }
                else -> { sx = x; sy = y }
            }
            val luma = bytes[sy * sample * width + sx * sample].toInt() and 255
            val gray = ((luma - 16) * 255 / 219).coerceIn(0, 255)
            pixels[y * outputWidth + x] = -0x1000000 or (gray shl 16) or (gray shl 8) or gray
        }
        bitmap.setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        faces.fill(null)
        val count = detector.findFaces(bitmap, faces)
        return (0 until count).any { (faces[it]?.confidence() ?: 0f) >= .4f }
    }

    fun close() { bitmap.recycle() }
}
