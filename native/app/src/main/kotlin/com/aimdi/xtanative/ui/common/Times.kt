package com.aimdi.xtanative.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aimdi.xtanative.R
import com.aimdi.xtanative.core.format.Ago
import com.aimdi.xtanative.core.format.Formats
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** "5m", "3h", "Oct 12": when a feed row's post was written. */
@Composable
fun agoText(then: Instant?): String {
    if (then == null) return ""
    val zone = ZoneId.systemDefault()
    val locale = locale()
    return when (val ago = Formats.ago(then, Instant.now()) { it.atZone(zone).year }) {
        is Ago.Seconds -> stringResource(R.string.ago_seconds, ago.n)
        is Ago.Minutes -> stringResource(R.string.ago_minutes, ago.n)
        is Ago.Hours -> stringResource(R.string.ago_hours, ago.n)
        is Ago.OnDate -> {
            val pattern = remember(locale, ago.sameYear) { DateFormat.getBestDateTimePattern(locale, if (ago.sameYear) "MMMd" else "yMMMd") }
            DateTimeFormatter.ofPattern(pattern, locale).format(ago.at.atZone(zone))
        }
    }
}

/** "3:04 PM · Oct 12, 2026": the time under an opened post, in the phone's 12- or 24-hour clock. */
@Composable
fun fullTimeText(then: Instant?): String {
    if (then == null) return ""
    val context = LocalContext.current
    val locale = locale()
    val time = DateFormat.getTimeFormat(context).format(Date.from(then))
    val date = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMd"), locale).format(then.atZone(ZoneId.systemDefault()))
    return "$time · $date"
}

/** 1.2K, 3.4M, with the locale's decimal separator. */
@Composable
fun countText(n: Long): String {
    val locale = locale()
    val decimal = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    return Formats.count(n, decimal)
}
