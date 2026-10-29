package com.calendarmods

import java.time.LocalDate

internal object WesternZodiac {
    private val starts = intArrayOf(20, 19, 21, 20, 21, 22, 23, 23, 23, 24, 23, 22)
    val names = listOf("摩羯座", "水瓶座", "双鱼座", "白羊座", "金牛座", "双子座", "巨蟹座", "狮子座", "处女座", "天秤座", "天蝎座", "射手座")
    fun index(date: LocalDate): Int = (date.monthValue - 1 + if (date.dayOfMonth >= starts[date.monthValue - 1]) 1 else 0) % 12
}
