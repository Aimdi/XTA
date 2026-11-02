package com.aimdi.xtanative.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
internal actual fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

internal actual fun datePattern(locale: Locale, skeleton: String): String = DateFormat.getBestDateTimePattern(locale, skeleton)

/** The phone's own time format, which follows its 12- or 24-hour setting rather than the locale's. */
@Composable
internal actual fun timeOfDayText(then: Instant, locale: Locale): String = DateFormat.getTimeFormat(LocalContext.current).format(Date.from(then))
