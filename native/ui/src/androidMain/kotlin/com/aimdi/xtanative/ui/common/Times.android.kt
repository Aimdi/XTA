package com.aimdi.xtanative.ui.common

import android.icu.text.CompactDecimalFormat
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.math.BigDecimal
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
internal actual fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

internal actual fun datePattern(locale: Locale, skeleton: String): String = DateFormat.getBestDateTimePattern(locale, skeleton)

/** The phone's own time format, which follows its 12- or 24-hour setting rather than the locale's. */
@Composable
internal actual fun timeOfDayText(then: Instant, locale: Locale): String {
    val context = LocalContext.current
    // One format per screen and locale, not one per post: the 12- or 24-hour setting is read as it is made.
    val format = remember(context, locale) { DateFormat.getTimeFormat(context) }
    return format.format(Date.from(then))
}

/**
 * ICU's compact number format (CLDR's short style), one per number of
 * decimals; used on the UI's thread. Null before Android 9: ICU 58 (Android
 * 8.0 and 8.1) keeps two significant digits whatever the decimals asked
 * (120K for 123,456), so X's own short counts are used there.
 */
internal actual fun compactCounts(locale: Locale): ((Long, Int) -> String)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val formats = (0..1).map { decimals ->
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).apply {
            roundingMode = BigDecimal.ROUND_DOWN
            maximumFractionDigits = decimals
            isGroupingUsed = true
        }
    }
    return { n, decimals -> formats[decimals.coerceIn(0, 1)].format(n) }
}
