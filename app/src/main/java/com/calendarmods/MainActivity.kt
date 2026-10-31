package com.calendarmods

import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var repository: ThemeRepository
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

    private val inventoryListener: (ThemeInventory) -> Unit = { updated ->
        Ui.post { applyInventory(updated) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("calendar", MODE_PRIVATE)
        styles = ThemeStyleStore(this)
        selectedId = preferences.getString("theme_id", null)
        root = FrameLayout(this)
        chooser = ChooserView(this)
        paper = PaperView(this)
        root.addView(chooser, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        chooser.onSelect = { theme -> enterImmersive(theme) }
        chooser.onStyle = { theme -> openStyleSheet(theme) }
        chooser.onStore = { startActivity(Intent(this, StoreActivity::class.java)) }
        chooser.onDelete = { theme -> confirmDelete(theme) }
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
                repository = loadedRepository
                inventory = loadedInventory
                styleValues = values
                data = calendar
                loaded = true
                repository.addListener(inventoryListener)
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
        if (loaded) repository.removeListener(inventoryListener)
        super.onDestroy()
    }

    private fun currentTheme(): CalendarTheme? =
        inventory.themes.firstOrNull { it.id == selectedId } ?: inventory.themes.firstOrNull()

    private fun currentStyle(theme: CalendarTheme?): ThemeStyle =
        theme?.let { styleValues[it.id] ?: it.defaultStyle() } ?: ThemeStyle()

    private fun refreshChooser() {
        chooser.setContent(inventory.themes, data, styleValues, environment, inventory, selectedId)
    }

    private fun applyInventory(updated: ThemeInventory) {
        inventory = updated
        styleValues = updated.themes.associate { it.id to styles.read(it) }
        if (updated.themes.none { it.id == selectedId }) {
            selectedId = updated.themes.firstOrNull()?.id
            preferences.edit().putString("theme_id", selectedId).apply()
        }
        if (immersive) {
            val theme = currentTheme()
            val calendar = data
            if (theme != null && calendar != null) {
                paper.setPage(theme, calendar, currentStyle(theme), environment, inventory, animate = false)
            }
        }
        refreshChooser()
    }

    private fun onNewDate(event: CalendarDateEvent) {
        Ui.io {
            val calendar = CalendarData.create(event.date)
            Ui.post {
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
        paper.setPage(theme, data, currentStyle(theme), environment, inventory, animate = false)
        paper.onLongPress = { exitImmersive() }
        paper.onTap = { }
        paper.onSwipe = { direction -> if (currentStyle(theme).interactive) browse(direction) }
        if (paper.parent == null) {
            root.addView(paper, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
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
        root.removeView(paper)
        todayButton?.let { root.removeView(it) }
        todayButton = null
        setSystemBars(immersive = false)
        chooseAmbient()
        refreshChooser()
    }

    private fun chooseAmbient() {
        ambientMonitor?.stop(); ambientMonitor = null
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
        val current = browsedDate ?: data?.today ?: return
        val next = if (theme.renderer.pagesByMonth) current.plusMonths(direction.toLong())
        else current.plusDays(direction.toLong())
        if (next < PlainDate.of(1, 2, 1) || next > PlainDate.of(9999, 11, 30)) return
        browsedDate = next
        Ui.io {
            val calendar = CalendarData.create(next)
            Ui.post {
                paper.setPage(theme, calendar, style, environment, inventory, animate = style.pageAnimation)
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
            params.bottomMargin = dp(28f).toInt()
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
                    startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_IMAGE)
                }
            })
        styleSheet = dialog
        dialog.setOnDismissListener { styleSheet = null }
        dialog.show()
    }

    private fun applyStyle(theme: CalendarTheme, updated: ThemeStyle) {
        styles.save(theme, updated)
        styleValues = styleValues + (theme.id to updated)
        styleSheet?.update(updated, environment, inventory)
        refreshChooser()
        if (immersive) refreshPage(animate = false)
        chooseAmbient()
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
                Ui.post { applyStyle(theme, styles.read(theme).copy(imagePath = path)) }
                Ui.io { styles.discard(old) }
            } catch (failure: Exception) {
                Ui.post { toast("无法读取这张图片，请选择其他图片。") }
            }
        }
    }

    private fun confirmDelete(theme: CalendarTheme) {
        if (inventory.themes.size <= 1) {
            toast("至少保留一个主题")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("删除主题？")
            .setMessage("将从已安装主题中移除“${theme.title.substringBefore(" · ")}”")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                Ui.io {
                    runCatching { repository.uninstall(theme.id) }
                        .onFailure { Ui.post { toast("删除失败，请重试") } }
                }
            }
            .show()
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

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val REQUEST_IMAGE = 41
    }
}
