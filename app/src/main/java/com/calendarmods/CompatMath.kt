package com.calendarmods

/** Floor arithmetic available on every supported Android release. */
internal object CompatMath {
    fun floorDiv(value: Long, divisor: Long): Long {
        val quotient = value / divisor
        return if ((value xor divisor) < 0 && quotient * divisor != value) quotient - 1 else quotient
    }
    fun floorMod(value: Long, divisor: Long): Long = value - floorDiv(value, divisor) * divisor
}
