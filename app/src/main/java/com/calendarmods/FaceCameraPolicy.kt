package com.calendarmods

internal data class FaceCameraInfo(val id: Int, val front: Boolean, val orientation: Int)

internal object FaceCameraPolicy {
    /** Camera IDs are opaque; front/screen-side cameras come first regardless of their IDs. */
    fun ordered(cameras: List<FaceCameraInfo>) = cameras.sortedWith(compareBy({ !it.front }, { it.id }))

    /** Rotate raw sensor pixels, without the mirror compensation used by a visible preview. */
    fun rotation(camera: FaceCameraInfo, displayDegrees: Int): Int =
        ((camera.orientation + (if (camera.front) displayDegrees else -displayDegrees)) % 360 + 360) % 360
}
