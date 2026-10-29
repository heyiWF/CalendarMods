package com.calendarmods

/** Qimen Dunjia Miji Daquan, vol. 19, three travel diagrams; see docs/traditional-theme.md.
 * Follow the diagrams, not the abridged prose (which omits Baihu-foot and repeats Tiandao).
 * Leap months reuse the nominal month's group. Other editions must use another profile ID.
 */
internal object ZhugeTravel {
    const val VERSION = "qimen-vol19-diagrams-v1"
    private val cycles = listOf(
        listOf("堂房", "金库", "金堂", "顺阳", "贼盗", "宝仓"),
        listOf("天盗", "天门", "天堂", "天财", "天贼", "天阳", "天侯", "天仓"),
        listOf("朱雀", "白虎头", "白虎肋", "白虎足", "玄武", "青龙头", "青龙肋", "青龙足"),
    )
    private val readings = mapOf(
        "堂房" to "旧说吉，出行通达，所求顺心。", "金库" to "旧说凶，行程易阻，宜慎出行。",
        "金堂" to "旧说吉，利于会友，商议有成。", "顺阳" to "旧说吉，往来顺遂，易逢相助。",
        "贼盗" to "旧说凶，出门宜慎，留心财物。", "宝仓" to "旧说吉，出行见喜，归途顺遂。",
        "天盗" to "旧说凶，慎防失物，宜缓行程。", "天门" to "旧说吉，诸事和合，去处通达。",
        "天堂" to "旧说吉，利见贵人，买卖通顺。", "天财" to "旧说吉，利于求财，交往和顺。",
        "天贼" to "旧说凶，求财不利，宜守慎行。", "天阳" to "旧说吉，往来相宜，所求顺心。",
        "天侯" to "旧说凶，谨言慎行，少起争执。", "天仓" to "旧说吉，诸事顺利，见人得喜。",
        "朱雀" to "旧说凶，谨慎行事，勿争口舌。", "白虎头" to "此图列凶；玉匣记另有吉说，版本有异。",
        "白虎肋" to "旧说吉，东西任行，易逢友人。", "白虎足" to "旧说凶，不宜远行，所求易阻。",
        "玄武" to "旧说凶，易生口舌，出行宜慎。", "青龙头" to "宜鸡鸣时或卯时出门，求财通达，百事大吉。",
        "青龙肋" to "旧说吉，求财遂心，往来顺意。", "青龙足" to "旧说凶，求财不易，凡事宜慎。",
    )
    fun name(month: Int, day: Int): String {
        require(month in 1..12 && day in 1..30)
        val cycle = cycles[(month - 1) % 3]
        return cycle[(day - 1) % cycle.size]
    }
    fun reading(month: Int, day: Int): String = name(month, day).let { "$it。${readings.getValue(it)}" }
}
