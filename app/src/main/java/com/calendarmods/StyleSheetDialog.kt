package com.calendarmods

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/** Framework replacement for the Material bottom sheet: same options, same persistence. */
internal class StyleSheetDialog(
    private val activity: android.app.Activity,
    private val theme: CalendarTheme,
    private var style: ThemeStyle,
    private val listener: Listener,
) : Dialog(activity) {

    interface Listener {
        fun onChanged(style: ThemeStyle)
    }

    private val content = LinearLayout(activity)
    private val bindings = mutableListOf<() -> Unit>()
    private var binding = false
    private var built = false
    private val dp = { value: Float -> value * activity.resources.displayMetrics.density }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun show() {
        if (built) { super.show(); return }
        built = true
        content.orientation = LinearLayout.VERTICAL
        val background = android.graphics.drawable.GradientDrawable()
        background.setColor(0xFFFBFAF6.toInt())
        val radius = dp(20f)
        background.cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        content.setBackgroundDrawable(background)
        val scroll = ScrollView(activity)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        // The content view receives margin-aware layout params: the decor view measures its
        // children with margins and casts, so a plain ViewGroup.LayoutParams crashes on resize.
        setContentView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        build()
        refreshBindings()
        window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(.55f)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.BOTTOM)
        super.show()
        fitToContent()
        if (MotionSettings.enabled(activity)) {
            scroll.startAnimation(android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0f).apply {
                duration = 220
                interpolator = android.view.animation.DecelerateInterpolator()
            })
        }
    }

    fun update(style: ThemeStyle) {
        this.style = style
        if (isShowing) refreshBindings()
    }

    private fun refreshBindings() {
        binding = true
        try { bindings.forEach { it() } } finally { binding = false }
    }

    /**
     * Keeps the sheet to 82% of the screen. The height goes on the window, not on the content
     * view's layout params: swapping those for a plain ViewGroup.LayoutParams makes the decor
     * view fail its MarginLayoutParams cast on the next measure pass.
     */
    private fun fitToContent() {
        val maxHeight = (activity.resources.displayMetrics.heightPixels * .82f).toInt()
        content.post {
            if (!isShowing) return@post
            val measured = content.height
            if (measured <= 0) return@post
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, min(measured, maxHeight))
        }
    }

    private fun apply(updated: ThemeStyle) {
        listener.onChanged(updated)
    }

    private fun build() {
        content.removeAllViews()
        content.setPadding(dp(24f).toInt(), dp(20f).toInt(), dp(24f).toInt(), dp(16f).toInt())
        heading(theme.title.substringBefore(" · ") + "样式", 22f)

        slider("字号", "整页按比例放大或缩小；空间不足时自动回收，不会重叠或超出屏幕。") {
            apply(style.copy(fontScale = it))
        }
        toggle("翻页动画", "日期切换时播放动画；关闭后直接切换。", { style.pageAnimation }) {
            apply(style.copy(pageAnimation = it))
        }
        val cameraAvailable = FaceBrightnessController.hasCamera()
        toggle("人脸感应亮屏",
            if (cameraAvailable) "仅全屏时使用摄像头本机检测。有人脸时提亮，连续 8 秒无人脸时降低亮度，日历仍可见。画面不保存、不上传。"
            else "此设备没有可用摄像头。",
            { style.faceBrightness }, enabled = cameraAvailable) {
            apply(style.copy(faceBrightness = it))
        }
        toggle("交互式日历", "全屏时左右滑动切换日期，点击“回到今天”恢复。", { style.interactive }) {
            apply(style.copy(interactive = it))
        }
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

    private fun heading(text: String, size: Float) {
        content.addView(label(text, size, 0xFF23201C.toInt()).apply {
            setPadding(0, dp(12f).toInt(), 0, dp(8f).toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
    }

    private fun label(text: String, size: Float, color: Int) = TextView(activity).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun toggle(title: String, description: String, checked: () -> Boolean, enabled: Boolean = true,
        onChange: (Boolean) -> Unit) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(title, 16f, 0xFF23201C.toInt()))
        column.addView(label(description, 13f, 0xFF6B6F66.toInt()))
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val box = CheckBox(activity).apply {
            isChecked = checked()
            isEnabled = enabled
            setOnCheckedChangeListener { _, value -> if (!binding) onChange(value) }
        }
        bindings += { box.isChecked = checked() }
        row.isEnabled = enabled
        row.alpha = if (enabled) 1f else .5f
        row.setOnClickListener { if (enabled) box.performClick() }
        row.minimumHeight = dp(72f).toInt()
        row.setPadding(0, dp(8f).toInt(), 0, dp(8f).toInt())
        row.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun divider() {
        content.addView(View(activity).apply { setBackgroundColor(0x1F000000) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(8f).toInt() })
    }

    /** Font scale: one step per percent, so the label and the thumb always agree. */
    private fun slider(title: String, description: String, onChange: (Float) -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6f).toInt(), 0, dp(6f).toInt())
        }
        val head = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(label(title, 16f, 0xFF23201C.toInt()).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val reading = label(percent(style.fontScale), 15f, 0xFF9A6B16.toInt()).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.END
        }
        head.addView(reading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(label(description, 13f, 0xFF6B6F66.toInt()).apply {
            setPadding(0, dp(2f).toInt(), 0, dp(4f).toInt())
        })

        val bar = android.widget.SeekBar(activity).apply {
            max = STEPS
            progress = stepOf(style.fontScale)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                    if (binding || !fromUser) return
                    val scale = ThemeStyle.clampFontScale(ThemeStyle.MIN_FONT_SCALE + value * STEP)
                    reading.text = percent(scale)
                    onChange(scale)
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
            })
        }
        bindings += {
            bar.progress = stepOf(style.fontScale)
            reading.text = percent(style.fontScale)
        }
        row.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f).toInt()))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun stepOf(scale: Float): Int =
        ((ThemeStyle.clampFontScale(scale) - ThemeStyle.MIN_FONT_SCALE) / STEP).toInt().coerceIn(0, STEPS)

    private fun percent(scale: Float): String =
        "${(ThemeStyle.clampFontScale(scale) * 100).toInt()}%"

    private companion object {
        const val STEP = .01f
        val STEPS = ((ThemeStyle.MAX_FONT_SCALE - ThemeStyle.MIN_FONT_SCALE) / STEP).toInt()
    }
}
