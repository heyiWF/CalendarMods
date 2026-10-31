package com.calendarmods

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PeitaiyiArtTest {
    @Test fun everySolarTermUsesActualCalendarDateAndBundledArtwork() {
        val first = PlainDate.of(2026, 1, 1)
        val seen = mutableSetOf<String>()
        repeat(365) { offset ->
            val date = first.plusDays(offset.toLong())
            val data = CalendarData.create(date)
            data.solarTerm?.let { term ->
                seen += term
                val day = Almanacs.shared.day(date)
                val feature = PeitaiyiArt.feature(data, day)!!
                // In 2026 rain water overlaps Spring Festival day two: the festival wins.
                if (day.festivalEdition == null) assertTrue(feature.second.startsWith("solar_terms_tinted/"))
                assertTrue(File("src/main/assets/peitaiyi/solar_terms_tinted/${PeitaiyiArt.terms.getValue(term)}.webp").isFile)
            }
        }
        assertEquals(24, seen.size)
        assertEquals(seen, PeitaiyiArt.terms.keys)
    }
    @Test fun festivalImagesFollowSharedLunarRulesAndAllAnimalsExist() {
        mapOf("2026-02-17" to "spring_festival", "2026-02-18" to "spring_festival", "2026-02-19" to "spring_festival",
            "2026-09-25" to "mid_autumn", "2026-10-01" to "national_day").forEach { (value, image) ->
            val date = PlainDate.parse(value)
            assertEquals("festivals_tinted/$image.webp", PeitaiyiArt.feature(CalendarData.create(date), Almanacs.shared.day(date))?.second)
        }
        assertEquals(12, PeitaiyiArt.animals.distinct().size)
        PeitaiyiArt.animals.forEach { assertTrue(File("src/main/assets/peitaiyi/zodiac/$it-mask.png").isFile) }
    }
}
