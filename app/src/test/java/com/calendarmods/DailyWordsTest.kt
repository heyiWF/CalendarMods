package com.calendarmods

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DailyWordsTest {
    @Test fun collectionsAreShortUniqueAndComplete() {
        assertEquals(366, DailyWords.mottos.size)
        assertEquals(366, DailyWords.mottos.toSet().size)
        assertTrue(DailyWords.mottos.all { it.length in 6..17 })
        assertEquals(366, DailyWords.heartWords.size)
        assertEquals(366, DailyWords.heartWords.toSet().size)
        assertTrue(DailyWords.heartWords.all { it.length == 2 })
    }

    @Test fun everyRollingYearIsUniqueIncludingLeapDayAndNewYear() {
        listOf("2026-10-12", "2027-12-31", "2028-02-28", "1969-12-31").forEach { start ->
            val days = List(366) { LocalDate.parse(start).plusDays(it.toLong()) }
            assertEquals(366, days.map(DailyWords::motto).toSet().size)
            assertEquals(366, days.map(DailyWords::heartWord).toSet().size)
            assertEquals(DailyWords.motto(days.first()), DailyWords.motto(days.first().plusDays(366)))
        }
    }

    @Test fun pairedAlmanacPagesKeepEveryEntryAndAnUnpaddedFinalItem() {
        val items = listOf("嫁娶", "祭祀", "出行", "开市", "纳财")
        assertEquals(listOf("嫁娶 · 祭祀", "出行 · 开市", "纳财"), almanacPages(items, 2))
        assertEquals(items, almanacPages(items, 1))
        assertEquals(listOf("暂无"), almanacPages(emptyList(), 2))
    }
}
