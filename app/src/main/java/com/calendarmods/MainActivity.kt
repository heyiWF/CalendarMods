package com.calendarmods

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var chooser: ChooserView
    private lateinit var paper: PaperView
    private lateinit var preferences: SharedPreferences
    private lateinit var faceBrightness: FaceBrightnessController
    private lateinit var darkMode: DarkModeController
    private var pendingFaceBrightness = false
    private lateinit var styles: ThemeStyleStore

    private var style = ThemeStyle()
    private var dark = false
    private var data: CalendarData? = null
    private var immersive = false
    private var browsedDate: PlainDate? = null
    private var todayButton: TextView? = null
    private var dayMonitor: CalendarDayMonitor? = null
    private var loaded = false
    private var styleSheet: StyleSheetDialog? = null
    private var resumed = false
    private var destroyed = false
    private var pageRequest = 0
    private var paging: CalendarPaging? = null
    private var pageOffset = 0
    private var screenAnimation: android.animation.ValueAnimator? = null

    private val theme: CalendarTheme get() = CalendarTheme.DAILY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("calendar", MODE_PRIVATE)
        styles = ThemeStyleStore(this)
        style = styles.read()
        darkMode = DarkModeController(this) { applyDark(it) }
        dark = darkMode.resolve(style)
        root = FrameLayout(this)
        chooser = ChooserView(this)
        if (Build.VERSION.SDK_INT >= 20) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                chooser.setWindowInsets(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
                insets
            }
        }
        paper = PaperView(this)
        root.addView(chooser, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            theme.scheme(dark).backdrop.toInt()))
        setSystemBars(immersive = false)
        faceBrightness = FaceBrightnessController(this, root, ::toast)
        pendingFaceBrightness = savedInstanceState?.getBoolean("pending_face_brightness") ?: false

        chooser.onSelect = { enterImmersive() }
        chooser.onStyle = { openStyleSheet() }

        Ui.io {
            val today = SystemCalendarClock.now().localDate()
            val calendar = CalendarData.create(today)
            Ui.post {
                if (destroyed) return@post
                data = calendar
                loaded = true
                refreshChooser()
                if (resumed) startMonitor()
                if (preferences.getBoolean("immersive", false)) enterImmersive()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!loaded) return
        startMonitor()
    }

    override fun onPause() {
        resumed = false
        faceBrightness.stop()
        stopMonitor()
        super.onPause()
    }

    private fun startMonitor() {
        stopMonitor()
        syncFaceBrightness()
        darkMode.start(style)
        dayMonitor = CalendarDayMonitor(this) { event -> onNewDate(event) }.also { it.start() }
    }

    private fun stopMonitor() {
        dayMonitor?.stop(); dayMonitor = null
        darkMode.stop()
    }

    override fun onDestroy() {
        faceBrightness.stop()
        destroyed = true
        pageRequest++
        screenAnimation?.cancel()
        styleSheet?.dismiss()
        stopMonitor()
        super.onDestroy()
    }

    private fun refreshChooser() {
        chooser.setContent(theme, data, style, dark)
    }

    private var dateRequest = 0

    private fun onNewDate(event: CalendarDateEvent) {
        val request = ++dateRequest
        Ui.io {
            val calendar = CalendarData.create(event.date)
            Ui.post {
                if (destroyed || request != dateRequest) return@post
                val previousToday = data?.today
                data = calendar
                if (browsedDate == null || browsedDate == previousToday) browsedDate = null
                refreshChooser()
                if (immersive) {
                    // Browsing another date stays put; only the live page follows midnight.
                    if (browsedDate == null) {
                        pageRequest++
                        pageOffset = 0
                        paging = CalendarPaging(calendar.today)
                        paper.setPage(theme, calendar, style, event.animate, dark)
                    }
                    updateTodayButton(calendar.today)
                }
            }
        }
    }

    private fun enterImmersive() {
        val data = data ?: return
        immersive = true
        browsedDate = null
        pageRequest++
        paging = CalendarPaging(data.today)
        pageOffset = 0
        paper.setPage(theme, data, style, animate = false, dark = dark)
        paper.onLongPress = { exitImmersive() }
        paper.onTap = { }
        paper.onSwipe = { direction -> if (style.interactive) browse(direction) }
        if (paper.parent == null) {
            root.addView(paper, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
        preferences.edit().putBoolean("immersive", true).apply()
        animateFullscreen(true)
        setSystemBars(immersive = true)
        updateTodayButton(data.today)
        syncFaceBrightness()
    }

    private fun exitImmersive() {
        if (!immersive) return
        immersive = false
        browsedDate = null
        preferences.edit().putBoolean("immersive", false).apply()
        paper.onLongPress = null
        paper.onSwipe = null
        pageRequest++
        animateFullscreen(false)
        todayButton?.let { root.removeView(it) }
        todayButton = null
        setSystemBars(immersive = false)
        refreshChooser()
        syncFaceBrightness()
    }

    private fun refreshPage(animate: Boolean) {
        if (!immersive) return
        val date = browsedDate ?: data?.today ?: return
        val pageData = if (date == data?.today) data!! else CalendarData.create(date)
        paper.setPage(theme, pageData, style, animate, dark)
    }

    /** Interactive mode: one day per swipe, clamped to the supported calendar range. */
    private fun browse(direction: Int) {
        if (!style.interactive) return
        val pager = paging ?: CalendarPaging(data?.today ?: return).also { paging = it }
        val target = (pager.home + pageOffset + direction).coerceIn(0, pager.count - 1)
        pageOffset = target - pager.home
        val next = pager.date(target)
        browsedDate = next
        val request = ++pageRequest
        Ui.io {
            val calendar = CalendarData.create(next)
            Ui.post {
                if (destroyed || !immersive || request != pageRequest) return@post
                paper.setPage(theme, calendar, style, animate = style.pageAnimation, dark = dark)
                data?.today?.let { updateTodayButton(it) }
            }
        }
    }

    private fun updateTodayButton(today: PlainDate?) {
        val showingOther = browsedDate != null && browsedDate != today
        if (!showingOther) {
            todayButton?.let { root.removeView(it) }
            todayButton = null
            return
        }
        val button = todayButton ?: TextView(this).apply {
            text = "回到今天"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(20f).toInt(), dp(10f).toInt(), dp(20f).toInt(), dp(10f).toInt())
            setBackgroundColor(0xCC20231E.toInt())
            setOnClickListener {
                browsedDate = null
                pageOffset = 0
                pageRequest++
                data?.let { calendar ->
                    paging = CalendarPaging(calendar.today)
                    paper.setPage(theme, calendar, style, style.pageAnimation, dark)
                }
                updateTodayButton(data?.today)
            }
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            params.bottomMargin = dp(72f).toInt()
            root.addView(this, params)
        }
        todayButton = button
        button.bringToFront()
    }

    private fun openStyleSheet() {
        val dialog = StyleSheetDialog(this, theme, style, object : StyleSheetDialog.Listener {
            override fun onChanged(updated: ThemeStyle) = applyStyle(updated)
        })
        styleSheet = dialog
        dialog.setOnDismissListener { styleSheet = null }
        dialog.show()
    }

    private fun applyStyle(updated: ThemeStyle) {
        if (updated.faceBrightness && !hasCameraPermission()) {
            pendingFaceBrightness = true
            styleSheet?.update(style)
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }
        if (style == updated) return
        style = updated
        styles.save(updated)
        darkMode.setStyle(updated)
        styleSheet?.update(updated)
        refreshChooser()
        if (immersive) refreshPage(animate = false)
        syncFaceBrightness()
    }

    /** The page and the window follow whichever printing the dark-mode source asked for. */
    private fun applyDark(value: Boolean) {
        if (dark == value) return
        dark = value
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            theme.scheme(dark).backdrop.toInt()))
        setSystemBars(immersive)
        if (loaded) refreshChooser()
        if (immersive) refreshPage(animate = false)
    }

    private fun hasCameraPermission() = Build.VERSION.SDK_INT < 23 ||
        checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun syncFaceBrightness() {
        if (resumed && immersive && style.faceBrightness && hasCameraPermission())
            faceBrightness.start()
        else faceBrightness.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_face_brightness", pendingFaceBrightness)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA) return
        val pending = pendingFaceBrightness
        pendingFaceBrightness = false
        if (hasCameraPermission()) {
            if (pending) applyStyle(style.copy(faceBrightness = true))
        } else toast("未授予摄像头权限，人脸感应亮屏保持关闭。可再次开启以授权。")
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun setSystemBars(immersive: Boolean) {
        val window = window
        var flags = View.SYSTEM_UI_FLAG_VISIBLE
        if (immersive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            flags = View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            if (Build.VERSION.SDK_INT >= 16) {
                flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            if (Build.VERSION.SDK_INT >= 19) flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        // 0x2000 is SYSTEM_UI_FLAG_LIGHT_STATUS_BAR (API 23): dark icons for the light sheet.
        // The flag is written as a literal so the constant is not resolved on older releases.
        if (!dark && Build.VERSION.SDK_INT >= 23) flags = flags or 0x00002000
        window.decorView.systemUiVisibility = flags
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (immersive) {
            exitImmersive()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    /** Transform the retained page layer; never render a full calendar bitmap per frame. */
    private fun animateFullscreen(entering: Boolean) {
        val reversing = screenAnimation?.isRunning == true
        val previousScale = paper.scaleX
        val previousAlpha = paper.alpha
        screenAnimation?.cancel()
        paper.pivotX = root.width / 2f
        paper.pivotY = root.height / 2f
        val start = if (reversing) previousScale else if (entering) .86f else 1f
        val startAlpha = if (reversing) previousAlpha else if (entering) 0f else 1f
        val end = if (entering) 1f else .86f
        if (!MotionSettings.enabled(this)) {
            paper.scaleX = 1f; paper.scaleY = 1f; paper.alpha = 1f
            if (!entering) root.removeView(paper)
            return
        }
        screenAnimation = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                val scale = start + (end - start) * progress
                paper.scaleX = scale; paper.scaleY = scale
                paper.alpha = startAlpha + ((if (entering) 1f else 0f) - startAlpha) * progress
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    if (!immersive) root.removeView(paper)
                    paper.scaleX = 1f; paper.scaleY = 1f; paper.alpha = 1f
                }
            })
            start()
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val REQUEST_CAMERA = 42
    }
}
