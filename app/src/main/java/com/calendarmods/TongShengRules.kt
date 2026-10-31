package com.calendarmods

import com.tyme.sixtycycle.EarthBranch
import com.tyme.sixtycycle.SixtyCycle
import com.tyme.solar.SolarDay

/** Versioned local rules. No random generation and no dependency on the current clock. */
internal object TongShengRules {
    const val VERSION = "tongsheng-local-v1"
    enum class ZodiacYearBoundary { LUNAR_NEW_YEAR, START_OF_SPRING }

    fun zodiacNumbers(date: PlainDate, animal: String,
        boundary: ZodiacYearBoundary = ZodiacYearBoundary.LUNAR_NEW_YEAR): List<Int> {
        val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val yearBranch = when (boundary) {
            ZodiacYearBoundary.LUNAR_NEW_YEAR -> solar.lunarDay.lunarMonth.lunarYear.sixtyCycle.earthBranch
            ZodiacYearBoundary.START_OF_SPRING -> solar.sixtyCycleDay.year.earthBranch
        }
        return (1..49).filter { yearBranch.next(1 - it).zodiac.name == animal }
    }

    // Tianyi common edition (Yi Tu Dan Jing): Jia/Wu/Geng share Chou/Wei.
    fun nobleHours(stemIndex: Int): String = listOf("丑未", "子申", "亥酉", "亥酉", "丑未", "子申", "丑未", "寅午", "卯巳", "卯巳")[stemIndex]

    fun dayLu(stemIndex: Int, dayBranch: String): String {
        val lu = listOf("寅", "卯", "巳", "午", "巳", "午", "申", "酉", "亥", "子")
        val incoming = lu.indices.filter { lu[it] == dayBranch }.joinToString("") { "甲乙丙丁戊己庚辛壬癸"[it].toString() }
        return "${lu[stemIndex]}命互禄" + if (incoming.isEmpty()) "" else "\n${incoming}命进禄"
    }

    /** Three/six harmony first, then same-element and remaining non-clashing branches.
     * This is our documented display order, not a reconstruction of publisher rankings. */
    fun luckyZodiacs(branch: EarthBranch): List<String> =
        (listOf(branch.combine, branch.next(4), branch.next(8)) +
            (0..11).map(EarthBranch::fromIndex).filter { it.element.name == branch.element.name } +
            (0..11).map { branch.next(it) })
            .distinctBy { it.index }.filter { it.index != branch.opposite.index && it.index != branch.harm.index }
            .take(6).map { it.zodiac.name }

    /** Decorative six-number column: cycle through the day's element's He Tu endings. */
    fun luckyNumbers(date: PlainDate, element: String): List<String> {
        val endings = when (element) { "水" -> setOf(1, 6); "火" -> setOf(2, 7); "木" -> setOf(3, 8); "金" -> setOf(4, 9); else -> setOf(0, 5) }
        val values = (1..49).filter { it % 10 in endings }
        val start = Math.floorMod(date.toEpochDay(), values.size.toLong()).toInt()
        return List(6) { values[(start + it) % values.size] }.sorted().map { it.toString().padStart(2, '0') }
    }
}

/** Original editorial copy, selected deterministically. These are cultural prompts, not predictions. */
internal object TongShengEditorial : AlmanacEditorialProvider {
    override val edition = "tongsheng-editorial-v1"
    private val homes = listOf(
        "大门为居室出入之处，宜保持整洁通畅。门前少堆杂物，归家自有清明气象。",
        "窗明几净，日光入室。晨起开窗，晚间收整，方寸居所亦可安放心绪。",
        "案头宜简，书卷有序。常用之物就近安放，留出舒展双手的一方天地。",
        "厅堂有序，动静相宜。家人相聚处留些空白，让交谈与来往从容自然。",
        "草木入室，贵在照料。依光照选择花木，不使枝叶阻碍通道，清新自来。",
        "厨房常清，器物各归其位。起居有节，一餐一饭皆是人间烟火。",
        "卧室重安静，灯光宜柔和。睡前收起纷杂，让夜色与心绪一同安定。",
        "庭前扫落叶，檐下听风声。屋宇不在大小，勤于照看便有长久的温暖。",
    )
    private val dreams = listOf(
        "梦见春山\n解：寄意向新而行", "梦见归舟\n解：寄意平安归家", "梦见明月\n解：寄意亲友团圆",
        "梦见书卷\n解：寄意学有所获", "梦见花开\n解：寄意欣欣向荣", "梦见清泉\n解：寄意心境澄明",
        "梦见稻田\n解：寄意勤作有成", "梦见长桥\n解：寄意彼此相通", "梦见飞燕\n解：寄意春信将至",
        "梦见灯火\n解：寄意有人相候", "梦见青松\n解：寄意坚定从容", "梦见瑞雪\n解：寄意岁月丰和",
    )
    override fun forDay(data: CalendarData): AlmanacEditorial {
        val date = data.today
        val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunarDay
        val branch = lunar.sixtyCycle.earthBranch
        val element = lunar.sixtyCycle.heavenStem.element.name
        fun index(size: Int) = Math.floorMod(date.toEpochDay(), size.toLong()).toInt()
        val observances = DeityCalendar.on(kotlin.math.abs(lunar.month), lunar.day, lunar.lunarMonth.isLeap)
        val birthYear = (lunar.year - 59 - (lunar.day - 1) % 12).coerceAtLeast(1)
        val monthBranch = lunar.lunarMonth.sixtyCycle.earthBranch
        val fortune = (birthYear..birthYear + 1).joinToString("\n") { year ->
            val cycle = SixtyCycle.fromIndex(year - 4)
            val animal = cycle.earthBranch
            val copy = when (animal.index) {
                monthBranch.combine.index, monthBranch.next(4).index, monthBranch.next(8).index -> "和合月，善结良缘。"
                monthBranch.opposite.index, monthBranch.harm.index -> "磨合月，处事从容。"
                else -> "平常月，勤勉有序。"
            }
            "${cycle.name}${animal.zodiac.name} ${year}年生人${lunar.lunarMonth.name}：$copy"
        }
        return AlmanacEditorial(
            fortune = fortune,
            fengShui = homes[index(homes.size)],
            zhuge = ZhugeTravel.reading(kotlin.math.abs(lunar.month), lunar.day),
            observances = observances.joinToString("，").ifEmpty { if (lunar.lunarMonth.isLeap) "闰月不重记神诞" else "本日无载神诞" },
            dream = dreams[index(dreams.size)], luckyNumbers = TongShengRules.luckyNumbers(date, element),
            zodiacNumbers = TongShengRules.zodiacNumbers(date, branch.combine.zodiac.name).joinToString(" ") { it.toString().padStart(2, '0') },
        )
    }
}
