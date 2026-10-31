package com.calendarmods


/** Calendar calculations and publisher-written columns are deliberately separate. */
internal data class AlmanacEditorial(
    val fortune: String = "暂无当日运程资料",
    val fengShui: String = "暂无风水文稿",
    val zhuge: String = "暂无择日文稿",
    val observances: String = "暂无民俗诞期资料",
    val dream: String = "暂无解梦文稿",
    val luckyNumbers: List<String> = emptyList(),
    val zodiacNumbers: String = "暂无生肖配数",
)

internal data class AlmanacHour(val branch: String, val start: String, val end: String, val luck: String)

internal data class AlmanacDay(
    val date: PlainDate,
    val dayZodiac: String,
    val westernZodiac: Int,
    val festivalEdition: FestiveDay?,
    val lunarDay: String,
    val lunarHeading: String,
    val hijriYear: String,
    val hijriMonth: String,
    val hijriDay: String,
    val termNotice: String,
    val festivals: String,
    val seasonalNote: String,
    val stem: String,
    val stemElement: String,
    val branch: String,
    val branchElement: String,
    val sound: String,
    val mansion: String,
    val duty: String,
    val goodGods: List<String>,
    val badGods: List<String>,
    val pillars: List<String>,
    val hours: List<AlmanacHour>,
    val directions: List<String>,
    val nobleBranches: String,
    val threeHarmony: String,
    val sixHarmony: String,
    val clash: String,
    val luckyZodiacs: List<String>,
    val nineStar: String,
    val ninePalace: List<Int>,
    val fetus: String,
    val pengZu: String,
    val dayLu: String,
    val recommends: List<String>,
    val avoids: List<String>,
    val editorial: AlmanacEditorial,
) {
    val accessibility: String get() = buildList {
        add("黄历，$date，农历$lunarHeading$lunarDay")
        add("回历$hijriYear 年 $hijriMonth $hijriDay 日，$termNotice，$festivals")
        add("宜：${recommends.joinToString("、")}；忌：${avoids.joinToString("、")}")
        add("天干$stem 属$stemElement，地支$branch 属$branchElement，纳音$sound，二十八宿$mansion，十二神$duty")
        add("吉神：${goodGods.joinToString("、")}，凶煞：${badGods.joinToString("、")}")
        add("${directions.joinToString("，")}，贵人时$nobleBranches，三合$threeHarmony，六合$sixHarmony，$clash")
        add("吉祥生肖（本版规则）：${luckyZodiacs.joinToString("、")}，日禄：$dayLu")
        add("九星$nineStar，九宫${ninePalace.joinToString()}，今日八字（子时）：${pillars.joinToString()}")
        add("时辰吉凶：${hours.joinToString { "${it.branch} ${it.start}至${it.end} ${it.luck}" }}")
        add("胎神$fetus，彭祖百忌$pengZu")
        add("运程：${editorial.fortune}")
        add("风水：${editorial.fengShui}，诸葛择日：${editorial.zhuge}，诞期：${editorial.observances}")
        add("周公解梦：${editorial.dream}，今日吉数：${editorial.luckyNumbers.ifEmpty { listOf("暂无资料") }.joinToString()}，${editorial.zodiacNumbers}，岁时：$seasonalNote")
    }.joinToString("。")

}
