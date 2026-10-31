package com.calendarmods

import android.content.Context
import android.os.Build
import android.provider.Settings

internal object MotionSettings {
    fun enabled(context: Context): Boolean = try {
        val resolver = context.contentResolver
        val scale = if (Build.VERSION.SDK_INT >= 17)
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        else Settings.System.getFloat(resolver, "animator_duration_scale", 1f)
        scale > 0f
    } catch (_: SecurityException) { true }
}
