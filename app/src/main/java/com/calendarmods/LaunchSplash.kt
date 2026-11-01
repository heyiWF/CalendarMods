package com.calendarmods

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.view.View
import android.view.animation.DecelerateInterpolator

/** The calendar header settles, the highlighted date wakes, then the page opens into the app. */
internal object LaunchSplash {
    fun install(activity: Activity): () -> Unit {
        var ready = false
        val decor = activity.window.decorView
        val deadline = android.os.SystemClock.uptimeMillis() + 1500L
        val firstFrame = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!ready && android.os.SystemClock.uptimeMillis() < deadline) return false
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        }
        decor.viewTreeObserver.addOnPreDrawListener(firstFrame)
        activity.splashScreen.setOnExitAnimationListener { splash ->
            if (!ValueAnimator.areAnimatorsEnabled()) {
                splash.remove()
                return@setOnExitAnimationListener
            }
            val animation = AnimatorSet()
            val fade = ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f)
            val icon = splash.iconView
            if (icon == null) animation.playTogether(fade)
            else animation.playTogether(fade,
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.06f),
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.06f),
                ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -8f * activity.resources.displayMetrics.density))
            val started = splash.iconAnimationStart
            val duration = splash.iconAnimationDuration
            animation.startDelay = if (started == null || duration == null) 0L else
                (duration.toMillis() - java.time.Duration.between(started, java.time.Instant.now()).toMillis()).coerceIn(0L, 600L)
            animation.duration = 220
            animation.interpolator = DecelerateInterpolator()
            animation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { splash.remove() }
                override fun onAnimationCancel(animation: Animator) { splash.remove() }
            })
            animation.start()
        }
        return { ready = true; decor.invalidate() }
    }
}
