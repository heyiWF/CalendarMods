package com.calendarmods

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable


/** Bundled SVGs converted with Android's Svg2Vector; see artwork/almanac/sources.json. */
internal class AlmanacIcons(private val context: Context) {
    private val cache = mutableMapOf<Int, Drawable>()
    private val animals = mapOf("鼠" to R.drawable.almanac_rat, "牛" to R.drawable.almanac_ox,
        "虎" to R.drawable.almanac_tiger, "兔" to R.drawable.almanac_rabbit, "龙" to R.drawable.almanac_dragon,
        "蛇" to R.drawable.almanac_snake, "马" to R.drawable.almanac_horse, "羊" to R.drawable.almanac_goat,
        "猴" to R.drawable.almanac_monkey, "鸡" to R.drawable.almanac_rooster, "狗" to R.drawable.almanac_dog, "猪" to R.drawable.almanac_pig)
    private val signs = listOf(R.drawable.almanac_zodiac_capricorn, R.drawable.almanac_zodiac_aquarius,
        R.drawable.almanac_zodiac_pisces, R.drawable.almanac_zodiac_aries, R.drawable.almanac_zodiac_taurus,
        R.drawable.almanac_zodiac_gemini, R.drawable.almanac_zodiac_cancer, R.drawable.almanac_zodiac_leo,
        R.drawable.almanac_zodiac_virgo, R.drawable.almanac_zodiac_libra, R.drawable.almanac_zodiac_scorpio,
        R.drawable.almanac_zodiac_sagittarius)
    fun animal(canvas: Canvas, name: String, x: Float, y: Float, size: Float, color: Int) =
        draw(canvas, animals.getValue(name), x, y, size, color)
    fun sign(canvas: Canvas, index: Int, x: Float, y: Float, size: Float, color: Int) =
        draw(canvas, signs[index], x, y, size, color)
    private fun draw(canvas: Canvas, id: Int, x: Float, y: Float, size: Float, color: Int) {
        val drawable = cache.getOrPut(id) { checkNotNull(context.getDrawable(id)).mutate() }
        canvas.save(); canvas.translate(x, y)
        drawable.setBounds(0, 0, size.toInt(), size.toInt())
        if (id == R.drawable.almanac_pig) {
            // This source uses a white knockout for the eye: tint only after compositing it.
            // Black becomes ink, white becomes transparent, including antialiased edges.
            val filter = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, android.graphics.Color.red(color).toFloat(),
                0f, 0f, 0f, 0f, android.graphics.Color.green(color).toFloat(),
                0f, 0f, 0f, 0f, android.graphics.Color.blue(color).toFloat(),
                -.2126f, -.7152f, -.0722f, 1f, 0f))) }
            canvas.saveLayer(0f, 0f, size, size, filter)
            drawable.draw(canvas); canvas.restore()
        } else { drawable.setTint(color); drawable.draw(canvas) }
        canvas.restore()
    }
}
