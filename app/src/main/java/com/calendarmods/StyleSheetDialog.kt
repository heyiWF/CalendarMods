package com.calendarmods

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Framework replacement for the Material bottom sheet: same options, same persistence. */
internal class StyleSheetDialog(
    private val activity: android.app.Activity,
    private val theme: CalendarTheme,
    private var style: ThemeStyle,
    private var environment: AlmanacEnvironment,
    private var inventory: ThemeInventory,
    private val listener: Listener,
) : Dialog(activity) {

    interface Listener {
        fun onChanged(style: ThemeStyle)
        fun onResetImage()
        fun onPickImage()
    }

    private val content = LinearLayout(activity)
    private val dp = { value: Float -> value * activity.resources.displayMetrics.density }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun show() {
        content.orientation = LinearLayout.VERTICAL
        val background = android.graphics.drawable.GradientDrawable()
        background.setColor(0xFFFBFAF6.toInt())
        val radius = dp(20f)
        background.cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        content.setBackgroundDrawable(background)
        val scroll = ScrollView(activity)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        build()
        val maxHeight = (activity.resources.displayMetrics.heightPixels * .82f).toInt()
        content.post {
            if (content.height > maxHeight) {
                scroll.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
                scroll.requestLayout()
            }
        }
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(.55f)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        super.show()
    }

    fun update(style: ThemeStyle, environment: AlmanacEnvironment, inventory: ThemeInventory) {
        this.style = style
        this.environment = environment
        this.inventory = inventory
        if (isShowing) { content.removeAllViews(); build() }
    }

    private fun apply(updated: ThemeStyle) {
        style = updated
        listener.onChanged(updated)
    }

    private fun build() {
        content.removeAllViews()
        content.setPadding(dp(24f).toInt(), dp(20f).toInt(), dp(24f).toInt(), dp(16f).toInt())
        heading(theme.title.substringBefore(" · ") + "样式", 22f)

        toggle("翻页动画", "日期切换时播放动画；关闭后直接切换。", style.pageAnimation) {
            apply(style.copy(pageAnimation = it))
        }
        toggle("交互式日历",
            if (theme.renderer.pagesByMonth) "全屏时左右滑动切换月份，点击“回到今天”恢复。"
            else "全屏时左右滑动切换日期，点击“回到今天”恢复。", style.interactive) {
            apply(style.copy(interactive = it))
        }
        divider()

        if (theme.renderer.isTongSheng) almanacOptions()
        fontOptions()
        imageOptions()
        divider()
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        actions.addView(Button(activity).apply {
            text = "完成"
            setOnClickListener { dismiss() }
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFF9A6B16.toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48f).toInt()))
        content.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun almanacOptions() {
        heading("字体颜色", 16f)
        val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var row: LinearLayout? = null
        AlmanacInk.entries.forEachIndexed { index, ink ->
            if (index % 3 == 0) {
                row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                labels.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val button = RadioButton(activity).apply {
                text = ink.title
                isChecked = style.ink == ink
                setTextColor(PAPER_INK)
                setOnClickListener { apply(style.copy(ink = ink)) }
            }
            row?.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(labels)

        toggle("节日特别版式", "节日换上花边与题签；时和主题同时展示节日和二十四节气插画。", style.festive) {
            apply(style.copy(festive = it))
        }
        divider()
        heading("夜间显示", 16f)
        toggle("深色模式", "手动保持暗色纸面、浅色文字。关闭后按下面的自动设置切换。", style.dark) {
            apply(style.copy(dark = it))
        }
        toggle("定时深色模式", "按设备当前时间切换，浏览其他日期不影响定时。", style.scheduledDark) {
            apply(style.copy(scheduledDark = it))
        }
        val times = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(true, false).forEach { start ->
            val minute = if (start) style.darkStart else style.darkEnd
            val button = Button(activity).apply {
                text = (if (start) "开始 " else "结束 ") + String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
                isEnabled = style.scheduledDark
                setOnClickListener {
                    TimePickerDialog(activity, { _, hour, minutes ->
                        apply(if (start) style.copy(darkStart = hour * 60 + minutes)
                        else style.copy(darkEnd = hour * 60 + minutes))
                    }, minute / 60, minute % 60, true).show()
                }
            }
            times.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(times)
        if (style.scheduledDark && style.darkStart == style.darkEnd) {
            note("开始与结束相同，定时暂不生效。")
        }
        toggle("环境光自适应",
            if (environment.lightAvailable) "持续暗 30 秒后开启；恢复明亮 10 秒后关闭。与定时同时开启时，满足任一条件即使用深色。"
            else "此设备没有可用的光线传感器，仍可使用手动或定时模式。",
            style.ambientDark, enabled = environment.lightAvailable) {
            apply(style.copy(ambientDark = it))
        }
        if (style.ambientDark) environment.lux?.let { note("当前环境光：${it.toInt()} lx") }
    }

    private fun fontOptions() {
        if (theme.fonts.isEmpty()) return
        divider()
        heading("日历字体", 16f)
        val defaultTitle = theme.fonts.firstOrNull { it.id == theme.defaultFont }?.title ?: "模板原字体"
        val choices = listOf(null to "跟随主题默认 · $defaultTitle", "template" to "模板原字体") +
            theme.fonts.map { it.id to it.title }
        choices.forEach { (id, title) ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val radio = RadioButton(activity).apply {
                isChecked = style.fontId == id
                setOnClickListener { apply(style.copy(fontId = id)) }
            }
            row.addView(radio, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            column.addView(label(title, 15f, PAPER_INK))
            val sample = label("日月成章 ABC 0123456789", 13f, 0xFF6B6F66.toInt())
            theme.resolveFont(id).let { fontId ->
                inventory.installed.firstOrNull { it.theme.id == theme.id }?.fontFamilies?.get(fontId)
                    ?.let { sample.typeface = it }
            }
            column.addView(sample)
            row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60f).toInt()))
        }
    }

    private fun imageOptions() {
        if (theme.renderer.image == null) return
        divider()
        heading("日历图片", 16f)
        val preview = ImageView(activity)
        preview.scaleType = ImageView.ScaleType.CENTER_CROP
        preview.setBackgroundColor(0xFFE7E5DC.toInt())
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128f).toInt()))
        loadPreview(preview)
        Button(activity).apply {
            text = "选择本机图片"
            setOnClickListener { listener.onPickImage() }
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f).toInt()))
        }
        divider()
        heading("图片填充方式", 16f)
        ImageFill.entries.filter { it.name.lowercase() in theme.settings
            .filterIsInstance<ThemeSetting.Choice>().firstOrNull { it.key == "fill" }?.choices.orEmpty() }
            .forEach { fill ->
                val button = RadioButton(activity).apply {
                    text = "${fill.title} · ${fill.description}"
                    isChecked = style.fill == fill
                    setTextColor(PAPER_INK)
                    setOnClickListener { apply(style.copy(fill = fill)) }
                }
                content.addView(button)
            }
        Button(activity).apply {
            text = "恢复默认图片与填充方式"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFF9A6B16.toInt())
            setOnClickListener { listener.onResetImage() }
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f).toInt()))
        }
    }

    private fun loadPreview(view: ImageView) {
        val target = dp(128f).toInt()
        Ui.io {
            val bitmap: Bitmap? = Ui.themeImage(activity, theme, style, inventory, 512)
            Ui.post { if (isShowing) { view.setImageBitmap(bitmap); view.scaleType = ImageView.ScaleType.CENTER_CROP } }
        }
    }

    private fun heading(text: String, size: Float) {
        content.addView(label(text, size, PAPER_INK).apply {
            setPadding(0, dp(12f).toInt(), 0, dp(8f).toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
    }

    private fun note(text: String) {
        content.addView(label(text, 12f, 0xFF6B6F66.toInt()).apply { setPadding(0, dp(4f).toInt(), 0, dp(4f).toInt()) })
    }

    private fun label(text: String, size: Float, color: Int) = TextView(activity).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun toggle(title: String, description: String, checked: Boolean, enabled: Boolean = true,
        onChange: (Boolean) -> Unit) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(title, 16f, PAPER_INK))
        column.addView(label(description, 13f, 0xFF6B6F66.toInt()))
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val box = CheckBox(activity).apply {
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f).toInt()))
    }

    private fun divider() {
        content.addView(View(activity).apply { setBackgroundColor(0x1F000000) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(8f).toInt() })
    }
}
