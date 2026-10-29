package com.calendarmods

import android.graphics.Paint
import android.graphics.Path
import java.util.Locale

/** A native print layout inspired by peitaiyi.com; all values come from AlmanacDay. */
internal fun TongShengPlate.webPage(height: Float, wide: Boolean) {
    if (wide) landscape(height, 900f, { webHero(it) }, { webGrid() })
    else {
        webHero(height - 900f)
        c.save(); c.translate(0f, height - 900f); webGrid(); c.restore()
    }
}

private fun TongShengPlate.webHero(bottom: Float) {
    val art = webArt ?: return
    val feature = if (festiveEnabled) PeitaiyiArt.feature(data, a) else null
    // Striped hanging board, brass pin heads and an inset wish plaque.
    box(24f, 16f, 1152f, 126f, true)
    p.color = paper
    repeat(38) { i -> c.drawRect(30f + i * 30f, 16f, 42f + i * 30f, 142f, p) }
    p.color = if (android.graphics.Color.red(paper) < 100) android.graphics.Color.rgb(58, 49, 32)
        else android.graphics.Color.rgb(221, 190, 112)
    c.drawRoundRect(174f, 36f, 1026f, 122f, 20f, 20f, p)
    listOf(78f, 1122f).forEach { c.drawCircle(it, 77f, 15f, p) }
    text(if (feature == null) "顺 时 安 康" else "岁 时 呈 祥", 190f, 45f, 820f, 65f, 52f)
    line(30f, 157f, 1170f, 157f, 4f)
    box(48f, 176f, 1104f, 112f, width = 3f)
    line(440f, 176f, 440f, 288f, 3f); line(760f, 176f, 760f, 288f, 3f)
    text(data.month.uppercase(Locale.ENGLISH), 64f, 182f, 360f, 98f, 57f, true)
    text(data.today.year.toString(), 452f, 178f, 296f, 104f, 90f, true)
    text(a.lunarHeading.substringAfter("年 "), 774f, 182f, 362f, 98f, 57f)
    text("${a.hijriYear} ${a.hijriMonth}", 50f, 299f, 550f, 37f, 27f, left = true)
    text("${data.weekday} · ${a.hijriDay}hb", 735f, 299f, 416f, 37f, 27f)
    val top = 350f
    val area = (bottom - top - 114f).coerceAtLeast(210f)
    if (feature == null) {
        text(data.today.dayOfMonth.toString(), 200f, top, 800f, area, 1050f, true)
        verticalFitted("清风入户添新意", 60f, top + 15f, 65f, area - 30f, 34f)
        verticalFitted("好日临门报吉祥", 1075f, top + 15f, 65f, area - 30f, 34f)
    } else {
        // The picture and the date occupy separate columns, never covering each other.
        art.draw(c, feature.second, 415f, top, 735f, area, ink, paper)
        text(data.today.dayOfMonth.toString(), 50f, top, 365f, area * .62f, 370f, true)
        text(feature.first.replace(" · ", "\n"), 52f, top + area * .65f, 350f, area * .28f, 57f, true)
    }
    text(a.festivals, 70f, bottom - 105f, 1060f, 35f, 28f)
    text(a.pengZu, 70f, bottom - 67f, 1060f, 30f, 25f)
    text("运程·编选 ${a.editorial.fortune}", 50f, bottom - 34f, 1100f, 30f, 20f)
}

private fun TongShengPlate.webGrid() {
    box(28f, 20f, 1144f, 830f, width = 4f)
    line(28f, 182f, 1172f, 182f, 3f)
    line(430f, 20f, 430f, 182f, 3f); line(770f, 20f, 770f, 182f, 3f)
    text(a.lunarDay, 43f, 33f, 372f, 88f, 78f, true)
    text(a.lunarHeading, 43f, 128f, 372f, 41f, 29f)
    // A fan-shaped central cartouche with a wide upper face for the solar-term text.
    p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f
    val fan = Path().apply { moveTo(452f, 42f); quadTo(600f, 15f, 748f, 42f); lineTo(700f, 118f)
        lineTo(620f, 166f); lineTo(580f, 166f); lineTo(500f, 118f); close() }
    c.drawPath(fan, p); p.style = Paint.Style.FILL
    text(a.termNotice, 463f, 49f, 274f, 55f, 40f, true)
    text("顺时而作", 519f, 109f, 162f, 36f, 26f)
    text(data.weekdayChinese, 784f, 34f, 374f, 86f, 72f, true)
    text(data.weekday.uppercase(Locale.ENGLISH), 784f, 125f, 374f, 40f, 31f, true)
    listOf(300f, 580f, 870f).forEach { line(it, 182f, it, 720f, 2.5f) }
    label("吉神方位", 65f, 199f, 198f)
    text(a.directions.joinToString("\n"), 42f, 248f, 244f, 159f, 30f, true)
    line(28f, 422f, 300f, 422f)
    compass(164f, 550f)
    words(a.editorial.dream.split(" "), 42f, 675f, 244f, 38f, 20f)
    label("宜", 407f, 199f, 66f, 32f)
    words(a.recommends, 316f, 250f, 248f, 223f, 34f, top = true)
    line(300f, 490f, 580f, 490f)
    label("忌", 407f, 502f, 66f, 32f)
    words(a.avoids, 316f, 550f, 248f, 156f, 32f, top = true)
    val facts = listOf("天干" to "${a.stem}属${a.stemElement}", "地支" to "${a.branch}属${a.branchElement}",
        "纳音" to a.sound, "廿八宿" to "${a.mansion}宿", "十二神" to "${a.duty}日")
    facts.forEachIndexed { i, (key, value) ->
        text(key, 590f, 202f + i * 38f, 110f, 35f, 27f)
        text(value, 701f, 202f + i * 38f, 159f, 35f, 29f, true)
    }
    line(580f, 402f, 870f, 402f)
    label("今日生肖", 629f, 414f, 193f)
    val opposite = a.clash.substringAfter("冲").take(1)
    webAnimal(a.dayZodiac, 596f, 467f, 117f)
    webAnimal(opposite, 739f, 467f, 117f)
    text(a.clash, 592f, 589f, 266f, 37f, 26f)
    text("${a.nineStar}\n三合${a.threeHarmony} · 六合${a.sixHarmony}", 592f, 638f, 266f, 69f, 25f)
    label("今日八字", 915f, 199f, 212f)
    pillars(883f, 247f, 276f, 110f)
    line(870f, 374f, 1172f, 374f)
    text("吉神", 882f, 387f, 277f, 30f, 26f, true)
    words(a.goodGods, 883f, 427f, 276f, 64f, 26f, top = true)
    text("凶煞", 882f, 503f, 277f, 30f, 26f, true)
    words(a.badGods, 883f, 544f, 276f, 64f, 26f, top = true)
    text("贵人时 ${a.nobleBranches}\n胎神 ${a.fetus}\n${a.dayLu}", 883f, 621f, 276f, 87f, 24f)
    line(28f, 720f, 1172f, 720f, 3f)
    hours(39f, 729f, 1122f)
    line(28f, 818f, 1172f, 818f)
    text("今日吉数 ${a.editorial.luckyNumbers.joinToString(" ")}", 40f, 820f, 556f, 29f, 24f)
    text("六合配数 ${a.editorial.zodiacNumbers}", 608f, 820f, 550f, 29f, 24f)
    box(28f, 861f, 1144f, 33f, true)
    text("时 和 · 电 子 老 黄 历", 40f, 861f, 547f, 33f, 24f, true, true)
    text(a.seasonalNote, 602f, 861f, 556f, 33f, 22f, inverse = true)
}

private fun TongShengPlate.webAnimal(name: String, x: Float, y: Float, size: Float) {
    unstretched(y + size / 2) { webArt?.animal(c, name, x, y, size, ink, paper) }
}
