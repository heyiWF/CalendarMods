package com.calendarmods


/** Host-owned data capability. No renderer, Android context, colour or font enters this contract. */
internal interface AlmanacEngine {
    val profile: AlmanacProfile
    fun calculate(date: PlainDate): AlmanacDay
}

internal data class AlmanacProfile(val id: String, val calendarRules: String, val folkRules: String,
    val editorialEdition: String)

/** Publisher/folk copy can be replaced without replacing the calendar calculations or any theme. */
internal interface AlmanacEditorialProvider {
    val edition: String
    fun forDay(data: CalendarData): AlmanacEditorial
}

/** One bounded, shared LRU per engine/profile. Rotation, thumbnails and all themes reuse the same day. */
internal class AlmanacRepository(val engine: AlmanacEngine, private val capacity: Int = 64) {
    init { require(capacity > 0) }
    private val days = LinkedHashMap<PlainDate, AlmanacDay>(capacity, .75f, true)
    @Synchronized fun day(date: PlainDate): AlmanacDay = days[date] ?: engine.calculate(date).also {
        require(it.date == date) { "Engine returned another date" }
        days[date] = it
        if (days.size > capacity) days.remove(days.keys.first())
    }
}

internal object Almanacs {
    val shared = AlmanacRepository(TymeAlmanacEngine())
}
