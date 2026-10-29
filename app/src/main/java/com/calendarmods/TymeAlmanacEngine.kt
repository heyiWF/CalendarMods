package com.calendarmods

import com.tyme.solar.SolarDay
import com.tyme.solar.SolarTime
import java.time.LocalDate

internal class TymeAlmanacEngine(private val editorials: AlmanacEditorialProvider = TongShengEditorial) : AlmanacEngine {
    // Malay spellings match the bilingual tear-off calendar tradition.
    private val hijriMonths = listOf("Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal",
        "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah")

    override val profile = AlmanacProfile("tyme-lunar-v1", "tyme4j-1.5.1/LunarDay", TongShengRules.VERSION, editorials.edition)
    override fun calculate(date: LocalDate): AlmanacDay {
        val data = CalendarData.create(date)
        val editorial = editorials.forDay(data)

        val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunarDay
        val day = lunar.sixtyCycle
        val stem = day.heavenStem
        val branch = day.earthBranch
        val hijri = solar.hijriDay
        val star = lunar.nineStar
        // LunarDay convention throughout, matching CalendarData's existing yi/ji.
        val hours = (0..11).map { index ->
            val hour = SolarTime.fromYmdHms(date.year, date.monthValue, date.dayOfMonth, index * 2, 0, 0).lunarHour
            AlmanacHour(hour.sixtyCycle.earthBranch.name,
                ((index * 2 + 23) % 24).toString().padStart(2, '0'),
                (index * 2 + 1).toString().padStart(2, '0'), hour.twelveStar.ecliptic.luck.name)
        }
        val midnight = SolarTime.fromYmdHms(date.year, date.monthValue, date.dayOfMonth, 0, 0, 0).lunarHour
        val term = (0..16).firstNotNullOfOrNull { offset ->
            solar.next(offset).termDay.takeIf { it.dayIndex == 0 }?.let {
                when (offset) { 0 -> "今日"; 1 -> "明日"; 2 -> "后日"; else -> "${offset}日后" } + it.solarTerm.name
            }
        }.orEmpty()
        return AlmanacDay(date, branch.zodiac.name, WesternZodiac.index(date), festiveDay(date), lunar.name + "日",
            "${lunar.lunarMonth.lunarYear.sixtyCycle.name}年 ${lunar.lunarMonth.name}${if (lunar.lunarMonth.dayCount == 30) "大" else "小"}",
            hijri.year.toString(), hijriMonths[hijri.month - 1], hijri.day.toString(), term,
            data.festivals.joinToString(" · ").ifEmpty { "岁时循序 · 日有所新" },
            listOf(term, data.festivals.joinToString(" · ").ifEmpty {
                DeityCalendar.on(kotlin.math.abs(lunar.month), lunar.day, lunar.lunarMonth.isLeap).joinToString(" · ")
                    .ifEmpty { solar.phenology.name }
            }).distinct().joinToString(" · "),
            stem.name, stem.element.name, branch.name, branch.element.name, day.sound.name,
            lunar.twentyEightStar.name, lunar.duty.name,
            lunar.gods.filter { it.luck.name == "吉" }.map { it.name },
            lunar.gods.filter { it.luck.name != "吉" }.map { it.name },
            listOf(midnight.sixtyCycleHour.year.name, midnight.sixtyCycleHour.month.name, midnight.sixtyCycleHour.day.name, midnight.sixtyCycle.name),
            hours, listOf("喜神${stem.joyDirection.name}", "阳贵${stem.yangDirection.name}",
                "阴贵${stem.yinDirection.name}", "财神${stem.wealthDirection.name}", "福神${stem.mascotDirection.name}"),
            TongShengRules.nobleHours(stem.index),
            branch.next(4).zodiac.name + branch.next(8).zodiac.name, branch.combine.zodiac.name,
            "${branch.zodiac.name}日冲${branch.opposite.zodiac.name} 煞${branch.ominous.name}",
            TongShengRules.luckyZodiacs(branch),
            star.toString(), listOf(4, 9, 2, 3, 5, 7, 8, 1, 6).map { (it + star.index + 4) % 9 + 1 },
            lunar.fetusDay.name, day.pengZu.toString(), TongShengRules.dayLu(stem.index, branch.name),
            data.recommends, data.avoids, editorial)
    }
}
