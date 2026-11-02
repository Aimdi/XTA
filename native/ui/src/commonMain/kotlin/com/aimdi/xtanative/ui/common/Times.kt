package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aimdi.xtanative.core.format.Ago
import com.aimdi.xtanative.core.format.Formats
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.ago_hours
import com.aimdi.xtanative.ui.generated.resources.ago_minutes
import com.aimdi.xtanative.ui.generated.resources.ago_seconds
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/** The locale the screens are drawn in. */
@Composable
internal expect fun currentLocale(): Locale

/** How [locale] writes the date [skeleton] asks for ("MMMd", "yMMMd", "yMMMM"): each platform knows its own. */
internal expect fun datePattern(locale: Locale, skeleton: String): String

/** The time of day of [then], in the 12- or 24-hour clock the system is set to. */
@Composable
internal expect fun timeOfDayText(then: Instant, locale: Locale): String

/** "5m", "3h", "Oct 12": when a feed row's post was written. */
@Composable
fun agoText(then: Instant?): String {
    if (then == null) return ""
    val zone = ZoneId.systemDefault()
    val locale = currentLocale()
    return when (val ago = Formats.ago(then, Instant.now()) { it.atZone(zone).year }) {
        is Ago.Seconds -> stringResource(Res.string.ago_seconds, ago.n)
        is Ago.Minutes -> stringResource(Res.string.ago_minutes, ago.n)
        is Ago.Hours -> stringResource(Res.string.ago_hours, ago.n)
        is Ago.OnDate -> {
            val pattern = remember(locale, ago.sameYear) { datePattern(locale, if (ago.sameYear) "MMMd" else "yMMMd") }
            DateTimeFormatter.ofPattern(pattern, locale).format(ago.at.atZone(zone))
        }
    }
}

/** "3:04 PM · Oct 12, 2026": the time under an opened post, in the system's 12- or 24-hour clock. */
@Composable
fun fullTimeText(then: Instant?): String {
    if (then == null) return ""
    val locale = currentLocale()
    val time = timeOfDayText(then, locale)
    val date = DateTimeFormatter.ofPattern(datePattern(locale, "yMMMd"), locale).format(then.atZone(ZoneId.systemDefault()))
    return "$time · $date"
}

/** "October 2026": the month, as a profile says when the account joined. */
@Composable
fun monthText(then: Instant): String {
    val locale = currentLocale()
    val pattern = remember(locale) { datePattern(locale, "yMMMM") }
    return DateTimeFormatter.ofPattern(pattern, locale).format(then.atZone(ZoneId.systemDefault()))
}

/** 1.2K, 3.4M, with the locale's decimal separator. */
@Composable
fun countText(n: Long): String {
    val locale = currentLocale()
    val decimal = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    return Formats.count(n, decimal)
}
