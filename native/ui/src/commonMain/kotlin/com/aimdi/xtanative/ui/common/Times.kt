package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aimdi.xtanative.core.format.Ago
import com.aimdi.xtanative.core.format.Formats
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.ago_hours
import com.aimdi.xtanative.ui.generated.resources.ago_minutes
import com.aimdi.xtanative.ui.generated.resources.ago_seconds
import com.aimdi.xtanative.ui.generated.resources.problem_saved_at
import com.aimdi.xtanative.ui.generated.resources.problem_saved_earlier
import com.aimdi.xtanative.ui.theme.AppLocale
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

/** "5m", "3h", "Oct 12": when a feed row's post was written, moving on at each whole minute while it is shown. */
@Composable
fun agoText(then: Instant?): String {
    if (then == null) return ""
    val zone = ZoneId.systemDefault()
    val locale = currentLocale()
    // Measured against the shared minute clock, so "5m" becomes "6m" while the feed stays open.
    val now = Instant.ofEpochMilli(minuteNow())
    return when (val ago = Formats.ago(then, now) { it.atZone(zone).year }) {
        is Ago.Seconds -> stringResource(Res.string.ago_seconds, ago.n)
        is Ago.Minutes -> stringResource(Res.string.ago_minutes, ago.n)
        is Ago.Hours -> stringResource(Res.string.ago_hours, ago.n)
        is Ago.OnDate -> {
            val formatter = remember(locale, ago.sameYear) { DateTimeFormatter.ofPattern(datePattern(locale, if (ago.sameYear) "MMMd" else "yMMMd"), locale) }
            formatter.format(ago.at.atZone(zone))
        }
    }
}

/** "3:04 PM · Oct 12, 2026": the time under an opened post, in the system's 12- or 24-hour clock. */
@Composable
fun fullTimeText(then: Instant?): String {
    if (then == null) return ""
    val locale = currentLocale()
    val time = timeOfDayText(then, locale)
    val formatter = remember(locale) { DateTimeFormatter.ofPattern(datePattern(locale, "yMMMd"), locale) }
    val date = formatter.format(then.atZone(ZoneId.systemDefault()))
    return "$time · $date"
}

/** "October 2026": the month, as a profile says when the account joined. */
@Composable
fun monthText(then: Instant): String {
    val locale = currentLocale()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern(datePattern(locale, "yMMMM"), locale) }
    return formatter.format(then.atZone(ZoneId.systemDefault()))
}

/** A count as the reader's language shortens it: 1.2K, 1,2 Mio., 1.2万, 12 тыс. ([countFormat]). */
@Composable
fun countText(n: Long): String {
    val locale = currentLocale()
    val format = remember(locale) { countFormat(locale) }
    return format(n)
}

/**
 * The platform's compact number format for [locale] ("1,2 Mio.", "1.2万"):
 * a count written with at most the given decimals, rounding down; null where
 * the platform has none.
 */
internal expect fun compactCounts(locale: Locale): ((Long, Int) -> String)?

/**
 * How [locale] shortens counts ([Formats.compactCount]), in Latin digits as
 * every other number in the app; made once per language and thread (the
 * platform's formats are not safe to share), not once per count on a card.
 * Latin Belarusian keeps X's own 1.2K: the platform knows only the Cyrillic words.
 */
internal fun countFormat(locale: Locale): (Long) -> String = countFormats.get().getOrPut(locale) { madeCountFormat(locale) }

private val countFormats: ThreadLocal<HashMap<Locale, (Long) -> String>> = ThreadLocal.withInitial { HashMap() }

private fun madeCountFormat(locale: Locale): (Long) -> String {
    val latin = runCatching { Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build() }.getOrDefault(locale)
    val decimal = DecimalFormatSymbols.getInstance(latin).decimalSeparator
    val compact = if (latin.country == AppLocale.LATIN_BELARUSIAN_REGION) null else runCatching { compactCounts(latin) }.getOrNull()
    return { n -> Formats.compactCount(n, decimal, compact) }
}

/**
 * "Showing posts saved at 14:05" for posts kept today, with the date for
 * older ones, or "Showing posts saved earlier" when it is not known (XTA's
 * StaleFeedBanner.ageLineOf): what a reader needs to judge whether a kept
 * feed is worth reading.
 */
@Composable
fun savedAtText(savedAt: Instant?): String {
    if (savedAt == null) return stringResource(Res.string.problem_saved_earlier)
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val time = timeOfDayText(savedAt, locale)
    val today = sameCalendarDay(savedAt, Instant.now(), zone)
    val shown = if (today) time else DateTimeFormatter.ofPattern(datePattern(locale, "yMMMd"), locale).format(savedAt.atZone(zone)) + " " + time
    return stringResource(Res.string.problem_saved_at, shown)
}

/** Whether [a] and [b] fall on the same day in [zone]: the calendar day, not 24 hours apart (XTA's sameCalendarDay). */
fun sameCalendarDay(a: Instant, b: Instant, zone: ZoneId): Boolean = a.atZone(zone).toLocalDate() == b.atZone(zone).toLocalDate()
