package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.settings.LocalLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/** The JVM's default, which the reader's language sets; read again when that changes. */
@Composable
internal actual fun currentLocale(): Locale {
    LocalLanguage.current
    return Locale.getDefault()
}

/**
 * Java 17, which the app ships with, has no date skeletons: the locale's own
 * medium date stands in, without its year where the skeleton has none, and
 * the whole month name beside the year for "yMMMM".
 */
internal actual fun datePattern(locale: Locale, skeleton: String): String {
    val medium = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale)
    return when (skeleton) {
        "yMMMM" -> "MMMM y"
        "MMMd" -> withoutYear(medium)
        else -> medium
    }
}

private val yearAtStart = Regex("^y+[^A-Za-z']*")
private val yearAtEnd = Regex("(?:[\\s,]*'[^']*')?[\\s,]*y+[^A-Za-z']*$")

/** "MMM d, y" to "MMM d", "dd.MM.y" to "dd.MM.", "y/MM/dd" to "MM/dd", "d 'de' MMM 'de' y" to "d 'de' MMM". */
private fun withoutYear(pattern: String): String =
    pattern.replace(yearAtStart, "").replace(yearAtEnd, "").ifBlank { pattern }

/** The locale's short time: "3:04 PM", "15:04". */
@Composable
internal actual fun timeOfDayText(then: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(then.atZone(ZoneId.systemDefault()))
