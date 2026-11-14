package com.calendarmods

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
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
    private var neighbourPages: Map<Int, CalendarData> = emptyMap()
    private var neighbourAnchor: PlainMonth? = null
    private var neighbourRequest = 0
    private var screenAnimation: android.animation.ValueAnimator? = null
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleReturn = Runnable {
        if (resumed && !touching && IdleReturn.armed(immersive, style.interactive, browsedDate != null))
            showToday(style.pageAnimation)
    }
    private val idleScreen = Runnable { showScreensaver() }
    private var screenSaver: ScreenSaverView? = null
    private var touching = false

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
        restartIdle()
    }

    override fun onPause() {
        resumed = false
        touching = false
        dismissScreensaver()
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
        cancelIdle()
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
                        prepareNeighbours(calendar.today)
                        // Nothing is left to return to; the idle clock keeps its own countdown.
                        idleHandler.removeCallbacks(idleReturn)
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
        paper.onPickDate = { date -> pickDate(date) }
        paper.onMonthStep = { direction -> flipMonth(direction) }
        paper.onMonthSettled = { date -> adoptDate(date) }
        paper.monthPage = { direction -> neighbourPages[direction] }
        paper.onMonthTurnStarted = {
            pageRequest++
            paper.data?.let { adoptDate(it.today) }
        }
        if (paper.parent == null) {
            root.addView(paper, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
        preferences.edit().putBoolean("immersive", true).apply()
        animateFullscreen(true)
        setSystemBars(immersive = true)
        updateTodayButton(data.today)
        prepareNeighbours(data.today)
        restartIdle()
        syncFaceBrightness()
    }

    private fun exitImmersive() {
        if (!immersive) return
        immersive = false
        browsedDate = null
        dismissScreensaver()
        preferences.edit().putBoolean("immersive", false).apply()
        paper.onLongPress = null
        paper.onSwipe = null
        paper.onPickDate = null
        paper.onMonthStep = null
        paper.onMonthSettled = null
        paper.monthPage = null
        paper.onMonthTurnStarted = null
        cancelNeighbours()
        cancelIdle()
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
        showDate(pager.date(target), style.pageAnimation)
    }

    /** Prints [date] on the page; [animate] turns the sheet the way a midnight change does. */
    private fun showDate(date: PlainDate, animate: Boolean) {
        browsedDate = if (date == data?.today) null else date
        paging?.let { pageOffset = it.page(date) - it.home }
        restartIdle()
        val request = ++pageRequest
        Ui.io {
            val calendar = CalendarData.create(date)
            Ui.post {
                if (destroyed || !immersive || request != pageRequest) return@post
                paper.setPage(theme, calendar, style, animate, dark)
                updateTodayButton(data?.today)
                prepareNeighbours(date)
            }
        }
    }

    /**
     * A tap on the month grid. A date inside the shown month changes the day; a date the grid
     * borrows from a neighbouring month turns the sheet into that month and lands on the day.
     */
    private fun pickDate(date: PlainDate) {
        if (!style.interactive || !CalendarPaging.supports(date)) return
        val current = browsedDate ?: data?.today ?: return
        if (date == current) return
        if (PlainMonth.from(date) == PlainMonth.from(current)) {
            showDate(date, style.pageAnimation)
        } else {
            flipTo(date, if (monthIndex(date) > monthIndex(current)) 1 else -1)
        }
    }

    /** Turns the sheet one month forward or back, landing on the first day of that month. */
    private fun flipMonth(direction: Int) {
        if (!style.interactive) return
        val current = browsedDate ?: data?.today ?: return
        val target = CalendarPaging.monthStep(current, direction) ?: return
        flipTo(target, direction)
    }

    /** Turns the sheet onto [date], which sits in a neighbouring month. */
    private fun flipTo(date: PlainDate, direction: Int) {
        val request = ++pageRequest
        Ui.io {
            val calendar = CalendarData.create(date)
            Ui.post {
                if (destroyed || !immersive || request != pageRequest) return@post
                paper.flipTo(calendar, direction, animate = style.pageAnimation)
            }
        }
    }

    private fun monthIndex(date: PlainDate) = date.year * 12 + date.monthValue

    /** The sheet landed on [date]: keep the pager, the today button and the warm months in step. */
    private fun adoptDate(date: PlainDate) {
        browsedDate = if (date == data?.today) null else date
        paging?.let { pageOffset = it.page(date) - it.home }
        updateTodayButton(data?.today)
        prepareNeighbours(date)
        restartIdle()
    }

    /**
     * Prints today again. The day is taken from the page's own clock when the countdown expires,
     * so one that ran past midnight lands on the new day instead of the one it started from.
     */
    private fun showToday(animate: Boolean) {
        val todayData = data
        val today = IdleReturn.target(SystemCalendarClock)
        browsedDate = null
        pageOffset = 0
        // Nothing is left to return to; the idle clock keeps its own countdown.
        idleHandler.removeCallbacks(idleReturn)
        val request = ++pageRequest
        if (todayData != null && todayData.today == today) {
            paging = CalendarPaging(today)
            paper.setPage(theme, todayData, style, animate, dark)
            updateTodayButton(today)
            prepareNeighbours(today)
            return
        }
        Ui.io {
            val calendar = CalendarData.create(today)
            Ui.post {
                if (destroyed || !immersive || request != pageRequest) return@post
                data = calendar
                paging = CalendarPaging(today)
                paper.setPage(theme, calendar, style, animate, dark)
                updateTodayButton(today)
                prepareNeighbours(today)
                refreshChooser()
            }
        }
    }

    /**
     * Restarts the silent countdown that takes a browsed page back to today. It runs only while
     * another date is on screen in interactive mode, and every touch or date change restarts it.
     */
    private fun restartIdle() {
        idleHandler.removeCallbacks(idleReturn)
        idleHandler.removeCallbacks(idleScreen)
        if (!immersive || !resumed || touching || screenSaver != null) return
        if (IdleReturn.armed(immersive, style.interactive, browsedDate != null)) {
            idleHandler.postDelayed(idleReturn, IdleReturn.WINDOW_MS)
        }
        if (ScreenSaver.armed(immersive, style.screensaver)) {
            idleHandler.postDelayed(idleScreen, ScreenSaver.WINDOW_MS)
        }
    }

    private fun cancelIdle() {
        idleHandler.removeCallbacks(idleReturn)
        idleHandler.removeCallbacks(idleScreen)
    }

    /** Hands the fullscreen page over to the idle clock; a touch brings the page back. */
    private fun showScreensaver() {
        if (!resumed || touching || screenSaver != null || !ScreenSaver.armed(immersive, style.screensaver)) return
        val view = ScreenSaverView(this).apply {
            hour24 = style.hour24
            onDismiss = { dismissScreensaver() }
        }
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        screenSaver = view
        // The calendar's marquee/ticker has no visible work while the opaque clock is up.
        paper.visibility = View.INVISIBLE
        todayButton?.visibility = View.INVISIBLE
        cancelIdle()
    }

    private fun dismissScreensaver() {
        screenSaver?.let { root.removeView(it) }
        screenSaver = null
        paper.visibility = View.VISIBLE
        todayButton?.visibility = View.VISIBLE
        restartIdle()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touching = true
            cancelIdle()
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            restartIdle()
        }
        return handled
    }

    /**
     * Prepares the page a month turn would reveal, so a drag can follow the finger from its first
     * pixel. Both neighbouring months are computed off the main thread, and every frame of the
     * turn then only has to print the page that is already in hand.
     */
    private fun prepareNeighbours(date: PlainDate) {
        val anchor = PlainMonth.from(date)
        if (anchor == neighbourAnchor) return
        neighbourAnchor = anchor
        neighbourPages = emptyMap()
        val request = ++neighbourRequest
        Ui.io {
            val pages = HashMap<Int, CalendarData>(4)
            CalendarPaging.monthStep(date, 1)?.let { pages[1] = CalendarData.create(it) }
            CalendarPaging.monthStep(date, -1)?.let { pages[-1] = CalendarData.create(it) }
            Ui.post { if (!destroyed && request == neighbourRequest) neighbourPages = pages }
        }
    }

    private fun cancelNeighbours() {
        neighbourRequest++
        neighbourAnchor = null
        neighbourPages = emptyMap()
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
            setOnClickListener { showToday(style.pageAnimation) }
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            params.bottomMargin = dp(72f).toInt()
            root.addView(this, params)
        }
        todayButton = button
        button.visibility = if (screenSaver == null) View.VISIBLE else View.INVISIBLE
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
        restartIdle()
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
        } else toast("人脸感应亮屏需要摄像头权限，再次开启即可授权。")
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
                // Lay the page out behind both bars: when the navigation bar hides, the page
                // already owns the space it vacates instead of leaving a strip of window colour.
                flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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
        if (screenSaver != null) {
            dismissScreensaver()
            return
        }
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
