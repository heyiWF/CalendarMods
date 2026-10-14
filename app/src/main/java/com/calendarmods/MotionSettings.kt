package com.calendarmods

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.withContext

/** Custom coroutine animations share the platform duration setting with Material transitions. */
internal fun systemAnimationScale(resolver: ContentResolver): Float =
    if (!ValueAnimator.areAnimatorsEnabled()) 0f
    else Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).coerceAtLeast(0f)

internal suspend fun withAnimationScale(scale: Float, block: suspend () -> Unit) =
    withContext(object : MotionDurationScale { override val scaleFactor = scale }) { block() }
