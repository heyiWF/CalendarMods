package com.calendarmods

import android.content.Context
import android.graphics.*
import android.util.LruCache
import kotlin.math.min
import kotlin.math.max

/** Artwork selection consumes shared calendar results; it performs no calendar calculations. */
internal object PeitaiyiArt {
    val animals = listOf("rat", "ox", "tiger", "rabbit", "dragon", "snake", "horse", "goat", "monkey", "rooster", "dog", "pig")
    val terms = listOf("小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至")
        .zip(listOf("xiaohan", "dahan", "lichun", "yushui", "jingzhe", "chunfen", "qingming", "guyu", "lixia", "xiaoman", "mangzhong", "xiazhi",
            "xiaoshu", "dashu", "liqiu", "chushu", "bailu", "qiufen", "hanlu", "shuangjiang", "lidong", "xiaoxue", "daxue", "dongzhi")).toMap()
    private val festivals = mapOf("元旦" to "new_year", "情人节" to "valentines_day", "妇女节" to "womens_day",
        "劳动节" to "labor_day", "青年节" to "youth_day", "护士节" to "nurses_day", "儿童节" to "childrens_day",
        "建党节" to "party_founding_day", "建军节" to "army_day", "教师节" to "teachers_day", "国庆节" to "national_day",
        "平安夜" to "christmas_eve", "圣诞节" to "christmas", "母亲节" to "mothers_day", "父亲节" to "fathers_day",
        "春节" to "spring_festival", "元宵节" to "lantern_festival", "端午节" to "dragon_boat", "七夕节" to "qixi", "七夕" to "qixi",
        "中秋节" to "mid_autumn", "重阳节" to "double_ninth", "腊八节" to "laba", "小年" to "little_new_year", "除夕" to "chuxi")
    fun feature(data: CalendarData, day: AlmanacDay): Pair<String, String>? {
        day.festivalEdition?.let { edition ->
            val key = when (edition.kind) {
                FestiveKind.NEW_YEAR -> "new_year"; FestiveKind.SPRING -> "spring_festival"
                FestiveKind.LANTERN -> "lantern_festival"; FestiveKind.DRAGON_BOAT -> "dragon_boat"
                FestiveKind.MOON -> "mid_autumn"; FestiveKind.NATIONAL -> "national_day"
            }
            return edition.title to "festivals_tinted/$key.webp"
        }
        data.solarTerm?.let { term -> terms[term]?.let { return term to "solar_terms_tinted/$it.webp" } }
        data.festivals.forEach { title -> festivals[title]?.let { return title to "festivals_tinted/$it.webp" } }
        return null
    }
}

/** Source PNG masks / WebP prints, decoded once into a bounded process-wide cache. */
internal class PeitaiyiArtwork(private val context: Context) {
    companion object {
        private val cache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = ThemeImages.bitmapBytes(value)
        }
    }
    private fun bitmap(path: String): Bitmap = synchronized(cache) {
        cache[path] ?: context.assets.open("peitaiyi/$path").use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = if (path.endsWith(".webp")) 2 else 1 })!!
        }.also { cache.put(path, it) }
    }
    fun draw(canvas: Canvas, path: String, x: Float, y: Float, w: Float, h: Float, ink: Int, paper: Int,
        mask: Boolean = false, cover: Boolean = false) {
        val image = bitmap(path)
        val scale = if (cover) max(w / image.width, h / image.height) else min(w / image.width, h / image.height)
        val left = x + (w - image.width * scale) / 2
        val top = if (cover) y else y + (h - image.height * scale) / 2
        val rect = RectF(left, top, left + image.width * scale, top + image.height * scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mask) paint.colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN)
        else {
            // Map the print's cream/red tones to the chosen paper/ink, including night palettes.
            val matrix = FloatArray(20)
            listOf(Color::red, Color::green, Color::blue).forEachIndexed { row, channel ->
                val delta = (channel(ink) - channel(paper)).toFloat()
                matrix[row * 5 + 1] = -delta / 200f
                matrix[row * 5 + 4] = channel(paper) + delta * 240f / 200f
            }
            matrix[18] = 1f
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        val checkpoint = canvas.save()
        try {
            if (cover) canvas.clipRect(x, y, x + w, y + h)
            canvas.drawBitmap(image, null, rect, paint)
        } finally { canvas.restoreToCount(checkpoint) }
    }
    fun animal(canvas: Canvas, name: String, x: Float, y: Float, size: Float, ink: Int, paper: Int) {
        val index = "鼠牛虎兔龙蛇马羊猴鸡狗猪".indexOf(name)
        if (index >= 0) draw(canvas, "zodiac/${PeitaiyiArt.animals[index]}-mask.png", x, y, size, size, ink, paper, true)
    }
}
