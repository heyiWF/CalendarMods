package com.calendarmods

import java.time.LocalDate
import com.tyme.solar.SolarDay

internal enum class FestiveKind { NEW_YEAR, SPRING, LANTERN, DRAGON_BOAT, MOON, NATIONAL }
internal data class FestiveDay(val title: String, val greeting: String, val kind: FestiveKind)
internal fun festiveDay(date: LocalDate): FestiveDay? {
    val lunar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunarDay
    if (!lunar.lunarMonth.isLeap) when {
        lunar.month == 1 && lunar.day in 1..3 -> return FestiveDay(
            listOf("春节 · 正月初一", "迎春 · 正月初二", "新禧 · 正月初三")[lunar.day - 1],
            listOf("一元复始 · 万象更新", "春和景明 · 阖家安康", "新岁呈祥 · 喜乐常在")[lunar.day - 1], FestiveKind.SPRING)
        lunar.month == 1 && lunar.day == 15 -> return FestiveDay("元宵佳节", "灯火团圆 · 人间长安", FestiveKind.LANTERN)
        lunar.month == 5 && lunar.day == 5 -> return FestiveDay("端午安康", "艾叶清芬 · 仲夏安康", FestiveKind.DRAGON_BOAT)
        lunar.month == 8 && lunar.day == 15 -> return FestiveDay("中秋团圆", "月满人间 · 千里共婵娟", FestiveKind.MOON)
    }
    return when {
        date.monthValue == 10 && date.dayOfMonth in 1..3 -> FestiveDay(
            if (date.dayOfMonth == 1) "国庆节" else "国庆佳节", "山河锦绣 · 家国同庆", FestiveKind.NATIONAL)
        date.monthValue == 1 && date.dayOfMonth == 1 -> FestiveDay("元旦新禧", "新岁启封 · 日日向新", FestiveKind.NEW_YEAR)
        else -> null
    }
}
