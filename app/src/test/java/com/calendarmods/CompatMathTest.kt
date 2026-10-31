package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Test

class CompatMathTest {
    @Test fun floorArithmeticMatchesNegativeEpochs() {
        for (value in listOf(-719528L, -146097L, -401L, -1L, 0L, 1L, 146097L, 2932896L)) {
            for (divisor in listOf(7L, 12L, 400L, 146097L)) {
                assertEquals(Math.floorDiv(value, divisor), CompatMath.floorDiv(value, divisor))
                assertEquals(Math.floorMod(value, divisor), CompatMath.floorMod(value, divisor))
            }
        }
    }
}
