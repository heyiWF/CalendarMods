package com.calendarmods

import androidx.compose.runtime.staticCompositionLocalOf

/** A host can substitute an engine once; every registered almanac renderer consumes the same contract. */
internal val LocalAlmanacRepository = staticCompositionLocalOf { Almanacs.shared }
