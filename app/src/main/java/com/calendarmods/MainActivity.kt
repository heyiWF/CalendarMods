package com.calendarmods

import android.app.Activity
import android.content.Intent
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
    private lateinit var styles: ThemeStyleStore

    private var inventory = ThemeInventory()
    private var styleValues: Map<String, ThemeStyle> = emptyMap()
    private var data: CalendarData? = null
    private var environment = AlmanacEnvironment()
    private var selectedId: String? = null
    private var immersive = false
    private var browsedDate: PlainDate? = null
    private var todayButton: TextView? = null
    private var dayMonitor: CalendarDayMonitor? = null
    private var ambientMonitor: AlmanacEnvironmentMonitor? = null
    private var loaded = false
    private var styleSheet: StyleSheetDialog? = null
    private var styleTheme: CalendarTheme? = null
    private var resumed = false
    private var destroyed = false
    private var pageRequest = 0
    private var paging: CalendarPaging? = null
    private var pageOffset = 0
    private var screenAnimation: android.animation.ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("calendar", MODE_PRIVATE)
        styles = ThemeStyleStore(this)
        selectedId = preferences.getString("theme_id", null)
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

        chooser.onSelect = { theme -> enterImmersive(theme) }
        chooser.onStyle = { theme -> openStyleSheet(theme) }
        chooser.onPageChanged = { theme ->
            if (theme != null) {
                selectedId = theme.id
                preferences.edit().putString("theme_id", theme.id).apply()
            }
        }

        Ui.io {
            val loadedRepository = ThemeRepository.get(this)
            val loadedInventory = loadedRepository.inventory
            val values = loadedInventory.themes.associate { it.id to styles.read(it) }
            val today = SystemCalendarClock.now().localDate()
            val calendar = CalendarData.create(today)
            Ui.post {
                if (destroyed) return@post
                inventory = loadedInventory
                styleValues = values
                data = calendar
                loaded = true
                refreshChooser()
                if (resumed) startMonitors()
                if (preferences.getBoolean("immersive", false)) {
                    inventory.themes.firstOrNull { it.id == selectedId }?.let { enterImmersive(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!loaded) return
        // The store writes the chosen theme to the same preference file.
        val stored = preferences.getString("theme_id", null)
        if (stored != null && stored != selectedId) {
            selectedId = stored
            refreshChooser()
            if (immersive) currentTheme()?.let { enterImmersive(it) }
        }
        startMonitors()
    }

    override fun onPause() {
        resumed = false
        stopMonitors()
        super.onPause()
    }

    private fun startMonitors() {
        stopMonitors()
        val theme = currentTheme()
        dayMonitor = CalendarDayMonitor(this) { event -> onNewDate(event) }.also { it.start() }
        ambientMonitor = AlmanacEnvironmentMonitor(this, theme?.renderer?.isTongSheng == true &&
            currentStyle(theme).ambientDark) { updated ->
            environment = updated
            if (immersive) refreshPage(animate = false) else refreshChooser()
        }.also { it.start() }
    }

    private fun stopMonitors() {
        dayMonitor?.stop(); dayMonitor = null
        ambientMonitor?.stop(); ambientMonitor = null
    }

    override fun onDestroy() {
        destroyed = true
        pageRequest++
        screenAnimation?.cancel()
        styleSheet?.dismiss()
        stopMonitors()
        super.onDestroy()
    }

    private fun currentTheme(): CalendarTheme? =
        inventory.themes.firstOrNull { it.id == selectedId } ?: inventory.themes.firstOrNull()

    private fun currentStyle(theme: CalendarTheme?): ThemeStyle =
        theme?.let { styleValues[it.id] ?: it.defaultStyle() } ?: ThemeStyle()

    private fun refreshChooser() {
        chooser.setContent(inventory.themes, data, styleValues, environment, inventory, selectedId)
    }

    private fun onNewDate(event: CalendarDateEvent) {
        Ui.io {
            val calendar = CalendarData.create(event.date)
            Ui.post {
                if (destroyed) return@post
                val previousToday = data?.today
                data = calendar
                if (browsedDate == null || browsedDate == previousToday) browsedDate = null
                refreshChooser()
                if (immersive) {
                    val theme = currentTheme() ?: return@post
                    // Browsing another date stays put; only the live page follows midnight.
                    if (browsedDate == null) {
                        paper.setPage(theme, calendar, currentStyle(theme), environment, inventory, event.animate)
                    }
                    updateTodayButton(calendar.today)
                }
            }
        }
    }

    private fun enterImmersive(theme: CalendarTheme) {
        val data = data ?: return
        selectedId = theme.id
        preferences.edit().putString("theme_id", theme.id).putBoolean("immersive", true).apply()
        immersive = true
        browsedDate = null
        pageRequest++
        paging = CalendarPaging(data.today, theme.renderer.pagesByMonth)
        pageOffset = 0
        paper.setPage(theme, data, currentStyle(theme), environment, inventory, animate = false)
        paper.onLongPress = { exitImmersive() }
        paper.onTap = { }
        paper.onSwipe = { direction -> if (currentStyle(theme).interactive) browse(direction) }
        if (paper.parent == null) {
            root.addView(paper, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
        animateFullscreen(true)
        setSystemBars(immersive = true)
        updateTodayButton(data.today)
        chooseAmbient()
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
        chooseAmbient()
        refreshChooser()
    }

    private fun chooseAmbient() {
        ambientMonitor?.stop(); ambientMonitor = null
        if (!resumed) return
        val theme = currentTheme()
        ambientMonitor = AlmanacEnvironmentMonitor(this, theme?.renderer?.isTongSheng == true &&
            currentStyle(theme).ambientDark) { updated ->
            environment = updated
            if (immersive) refreshPage(animate = false) else refreshChooser()
        }.also { it.start() }
    }

    private fun refreshPage(animate: Boolean) {
        if (!immersive) return
        val theme = currentTheme() ?: return
        val date = browsedDate ?: data?.today ?: return
        val pageData = if (date == data?.today) data!! else CalendarData.create(date)
        paper.setPage(theme, pageData, currentStyle(theme), environment, inventory, animate)
    }

    /** Interactive mode: one day, or one month for the paged-by-month renderers. */
    private fun browse(direction: Int) {
        val theme = currentTheme() ?: return
        val style = currentStyle(theme)
        if (!style.interactive) return
        val pager = paging ?: CalendarPaging(data?.today ?: return, theme.renderer.pagesByMonth).also { paging = it }
        val target = (pager.home + pageOffset + direction).coerceIn(0, pager.count - 1)
        pageOffset = target - pager.home
        val next = pager.date(target)
        browsedDate = next
        val request = ++pageRequest
        Ui.io {
            val calendar = CalendarData.create(next)
            Ui.post {
                if (destroyed || !immersive || request != pageRequest) return@post
                paper.setPage(theme, calendar, currentStyle(theme), environment, inventory, animate = style.pageAnimation)
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
                    currentTheme()?.let { theme ->
                        paper.setPage(theme, calendar, currentStyle(theme), environment, inventory,
                            currentStyle(theme).pageAnimation)
                    }
                }
                updateTodayButton(today)
            }
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            params.bottomMargin = dp(72f).toInt()
            root.addView(this, params)
        }
        todayButton = button
        button.bringToFront()
    }

    private fun openStyleSheet(theme: CalendarTheme) {
        styleTheme = theme
        val dialog = StyleSheetDialog(this, theme, currentStyle(theme), environment, inventory,
            object : StyleSheetDialog.Listener {
                override fun onChanged(updated: ThemeStyle) = applyStyle(theme, updated)

                override fun onResetImage() {
                    val old = currentStyle(theme).imagePath
                    styles.resetImage(theme)
                    applyStyle(theme, styles.read(theme))
                    Ui.io { styles.discard(old) }
                }

                override fun onPickImage() {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "image/*"
                    try { startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_IMAGE) }
                    catch (_: android.content.ActivityNotFoundException) { toast("此设备没有可用的图片选择器。") }
                }
            })
        styleSheet = dialog
        dialog.setOnDismissListener { styleSheet = null }
        dialog.show()
    }

    private fun applyStyle(theme: CalendarTheme, updated: ThemeStyle) {
        val previous = currentStyle(theme)
        if (previous == updated) return
        styles.save(theme, updated)
        styleValues = styleValues + (theme.id to updated)
        styleSheet?.update(updated, environment, inventory)
        refreshChooser()
        if (immersive) refreshPage(animate = false)
        if (previous.ambientDark != updated.ambientDark) chooseAmbient()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val theme = styleTheme ?: return
        Ui.io {
            try {
                val path = styles.importImage(theme, uri)
                val old = currentStyle(theme).imagePath
                Ui.post { if (!destroyed) applyStyle(theme, styles.read(theme).copy(imagePath = path))
                    else { styles.save(theme, styles.read(theme).copy(imagePath = path)) } }
                Ui.io { styles.discard(old) }
            } catch (failure: Exception) {
                Ui.post { toast("无法读取这张图片，请选择其他图片。") }
            }
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun setSystemBars(immersive: Boolean) {
        val window = window
        if (immersive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            var flags = View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            if (Build.VERSION.SDK_INT >= 16) {
                flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            if (Build.VERSION.SDK_INT >= 19) flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            window.decorView.systemUiVisibility = flags
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
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
        screenAnimation?.cancel()
        paper.pivotX = root.width / 2f
        paper.pivotY = root.height / 2f
        val start = if (entering) .86f else paper.scaleX
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
                paper.alpha = if (entering) progress else 1f - progress
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!immersive) root.removeView(paper)
                    paper.scaleX = 1f; paper.scaleY = 1f; paper.alpha = 1f
                }
            })
            start()
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val REQUEST_IMAGE = 41
    }
}
