package com.calendarmods

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Framework replacement for the Compose theme store: three tabs, previews, install and reorder. */
class StoreActivity : Activity() {
    private lateinit var repository: ThemeRepository
    private lateinit var root: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var editAction: TextView

    private var inventory = ThemeInventory()
    private var tab = 0
    private var editing = false
    private var draft: List<String> = emptyList()
    private var draggingId: String? = null
    private var data: CalendarData? = null

    private val tabNames = listOf("推荐", "收藏", "已安装")
    private val density get() = resources.displayMetrics.density

    private val listener: (ThemeInventory) -> Unit = { updated -> Ui.post { render(updated) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ThemeRepository.get(this)
        inventory = repository.inventory
        draft = inventory.library.order
        data = CalendarData.create(SystemCalendarClock.now().localDate())
        buildShell()
        repository.addListener(listener)
        render(inventory)
    }

    override fun onDestroy() {
        repository.removeListener(listener)
        super.onDestroy()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFAF9F5.toInt())
        }
        setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f).toInt(), dp(6f).toInt(), dp(8f).toInt(), dp(6f).toInt())
        }
        bar.addView(text("‹", 30f, PAPER_INK).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()))
        bar.addView(text("主题商店", 18f, PAPER_INK).apply {
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        editAction = text("编辑", 14f, PAPER_INK).apply {
            gravity = Gravity.CENTER
            setOnClickListener { toggleEditing() }
        }
        bar.addView(editAction, LinearLayout.LayoutParams(dp(56f).toInt(), dp(48f).toInt()))
        root.addView(bar)

        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabNames.forEachIndexed { index, name ->
            val item = text(name, 15f, PAPER_INK).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10f).toInt(), 0, dp(10f).toInt())
                setOnClickListener { selectTab(index) }
            }
            tabs.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabs)
        root.addView(View(this).apply { setBackgroundColor(0x1A000000) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        val scroll = ScrollView(this)
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f).toInt(), dp(16f).toInt(), dp(16f).toInt(), dp(24f).toInt())
        }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun selectTab(index: Int) {
        if (tab == index) return
        tab = index
        editing = false
        draft = inventory.library.order
        render(inventory)
    }

    private fun toggleEditing() {
        if (tab != 2) return
        editing = !editing
        if (!editing) commitOrder()
        render(inventory)
    }

    private fun commitOrder() {
        if (draft == inventory.library.order) return
        Ui.io { runCatching { repository.reorder(draft) } }
    }

    private fun render(updated: ThemeInventory) {
        inventory = updated
        if (draft.isEmpty() || draft.toSet() != updated.library.order.toSet()) draft = updated.library.order
        editAction.visibility = if (tab == 2) View.VISIBLE else View.INVISIBLE
        editAction.text = if (editing) "完成" else "编辑"
        for (i in 0 until tabs.childCount) {
            (tabs.getChildAt(i) as TextView).setTextColor(if (i == tab) 0xFF9A6B16.toInt() else 0xFF6B6F66.toInt())
        }
        grid.removeAllViews()
        val themes = when (tab) {
            0 -> CalendarTheme.entries
            1 -> inventory.catalog.filter { it.id in inventory.library.favorites }
            else -> draft.mapNotNull(inventory.byId::get)
        }
        if (themes.isEmpty()) {
            grid.addView(text("收藏喜欢的主题，在这里轻松找到", 14f, 0xFF7A7E75.toInt()).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48f).toInt(), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        themes.chunked(3).forEach { rowThemes ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowThemes.forEach { theme ->
                row.addView(card(theme), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(dp(6f).toInt(), 0, dp(6f).toInt(), dp(20f).toInt()) })
            }
            repeat(3 - rowThemes.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    setMargins(dp(6f).toInt(), 0, dp(6f).toInt(), 0)
                })
            }
            grid.addView(row)
        }
    }

    private fun card(theme: CalendarTheme): View {
        val installed = theme.id in inventory.library.order
        val favorite = theme.id in inventory.library.favorites
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val preview = FrameLayout(this)
        val image = ImageView(this)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setBackgroundColor(theme.surfaces.paper.toInt())
        preview.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        val ratio = resources.displayMetrics.widthPixels.toFloat() / resources.displayMetrics.heightPixels
        preview.addView(View(this), FrameLayout.LayoutParams(1, 1))
        column.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (widthGuess() / 3f / ratio).toInt()))
        loadPreview(theme, image)

        if (editing && tab == 2) {
            val drag = text("≡", 18f, PAPER_INK).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(0xE6FFFFFF.toInt())
                setOnLongClickListener {
                    draggingId = theme.id
                    startDrag(android.content.ClipData.newPlainText("", ""),
                        View.DragShadowBuilder(this), theme.id, 0)
                    true
                }
            }
            preview.addView(drag, FrameLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt(),
                Gravity.TOP or Gravity.START))
            val remove = text("×", 20f, 0xFFB3261E.toInt()).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(0xE6FFFFFF.toInt())
                setOnClickListener { confirmDelete(theme) }
            }
            preview.addView(remove, FrameLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt(),
                Gravity.TOP or Gravity.END))
        } else {
            val heart = text(if (favorite) "♥" else "♡", 18f,
                if (favorite) 0xFFB3261E.toInt() else PAPER_INK).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(0xE6FFFFFF.toInt())
                setOnClickListener {
                    Ui.io { runCatching { repository.toggleFavorite(theme.id) } }
                }
            }
            preview.addView(heart, FrameLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt(),
                Gravity.TOP or Gravity.END))
        }
        preview.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    preview.setBackgroundColor(0x33BA811B); true
                }
                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    preview.setBackgroundColor(Color.TRANSPARENT); true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    preview.setBackgroundColor(Color.TRANSPARENT)
                    dropOn(theme.id)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    preview.setBackgroundColor(Color.TRANSPARENT)
                    draggingId = null
                    false
                }
                else -> false
            }
        }

        column.addView(text(theme.title.substringBefore(" · "), 13f, PAPER_INK).apply {
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(0, dp(8f).toInt(), 0, dp(4f).toInt())
        })
        val action = text(when {
            tab == 2 -> "使用"
            installed -> "已安装"
            else -> "应用"
        }, 13f, if (installed && tab != 2) 0xFF6B6F66.toInt() else 0xFF9A6B16.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8f).toInt(), 0, dp(8f).toInt())
            setBackgroundDrawable(rounded(0x149A6B16, dp(6f)))
            setOnClickListener {
                when {
                    tab == 2 -> use(theme)
                    installed -> confirmDelete(theme)
                    else -> install(theme)
                }
            }
        }
        column.addView(action)
        return column
    }

    private fun dropOn(targetId: String) {
        val dragged = draggingId ?: return
        if (dragged == targetId) return
        val values = draft.toMutableList()
        val from = values.indexOf(dragged)
        val to = values.indexOf(targetId)
        if (from < 0 || to < 0) return
        values.removeAt(from)
        values.add(to, dragged)
        draft = values
        Ui.io {
            runCatching { repository.reorder(draft) }
            Ui.post { render(repository.inventory) }
        }
    }

    private fun install(theme: CalendarTheme) {
        Ui.io {
            runCatching { repository.add(theme.id) }
                .onFailure { Ui.post { toast("应用失败，请重试") } }
        }
    }

    private fun use(theme: CalendarTheme) {
        getSharedPreferences("calendar", MODE_PRIVATE).edit()
            .putString("theme_id", theme.id).apply()
        setResult(RESULT_OK)
        finish()
    }

    private fun confirmDelete(theme: CalendarTheme) {
        if (inventory.library.order.size <= 1) { toast("至少保留一个主题"); return }
        android.app.AlertDialog.Builder(this)
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

    private fun loadPreview(theme: CalendarTheme, view: ImageView) {
        val data = data ?: return
        val style = inventory.themes.firstOrNull { it.id == theme.id }?.let { theme.defaultStyle() }
            ?: theme.defaultStyle()
        val width = (widthGuess() / 3f).toInt().coerceAtLeast(1)
        val ratio = resources.displayMetrics.widthPixels.toFloat() / resources.displayMetrics.heightPixels
        val height = (width / ratio).toInt().coerceAtLeast(1)
        val key = PreviewCache.key(theme, data, style, AlmanacEnvironment(), width, height)
        PreviewCache.get(key)?.let { view.setImageBitmap(it); return }
        Ui.io {
            val bitmap: Bitmap? = PreviewCache.render(this, key, theme, data, style,
                AlmanacEnvironment(), width, height, inventory)
            Ui.post { if (bitmap != null && !bitmap.isRecycled) view.setImageBitmap(bitmap) }
        }
    }

    private fun widthGuess(): Float = resources.displayMetrics.widthPixels - dp(32f)

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Float) = value * density

    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}
