package com.aimdi.xtanative.plugins.stocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.aimdi.xtanative.networks.stocks.ChangeWindow
import com.aimdi.xtanative.networks.stocks.ClosedTag
import com.aimdi.xtanative.networks.stocks.NumberSymbols
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.networks.stocks.TickerKind
import com.aimdi.xtanative.networks.stocks.Trend
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** X's own green and red, so a rising price reads the same here as on a cashtag in the timeline. */
private val Up = Color(0xFF00BA7C)
private val Down = Color(0xFFF4212E)

/** X's amber: a caution about a row or a post, never a price move. */
internal val Amber = Color(0xFFFFAD1F)

/** Figures of one width, so prices in a column and a refreshed price keep their place. */
internal const val TABULAR = "tnum"

/** The numbers of the reader's language, as XTA formats prices in it. */
@Composable
internal fun numberSymbols(): NumberSymbols {
    val language = currentLanguage()
    return remember(language) { NumberSymbols.forLocale(language) }
}

/** Green for a gain, red for a loss, readable on [background]; unknown or flat is neutral. */
@Composable
internal fun trendColor(percent: Double?, background: Color = MaterialTheme.colorScheme.background): Color = when (StockNumbers.trend(percent)) {
    Trend.UP -> readable(Up, background)
    Trend.DOWN -> readable(Down, background)
    Trend.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun warningColor(background: Color = MaterialTheme.colorScheme.background): Color = readable(Amber, background)

/** The colour a live market's dot is drawn in. */
@Composable
internal fun liveColor(): Color = readable(Up, MaterialTheme.colorScheme.background)

/** The accent Stocks draws selection with (a tab, a strip entry, the reading shown), readable on the background. */
@Composable
internal fun stocksAccent(): Color = readable(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)

/** [color], or the nearest lighter or darker one that reads at 4.5:1 on [background], as XTA's ensureContrast. */
internal fun readable(color: Color, background: Color, ratio: Float = 4.5f): Color {
    if (contrast(color, background) >= ratio) return color
    val target = if (background.luminance() > 0.18f) Color.Black else Color.White
    return (1..20).asSequence().map { lerp(color, target, it / 20f) }.firstOrNull { contrast(it, background) >= ratio } ?: target
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** "Close Fri", or "Close Oct 30" for a week-old close; "Market closed" when the quote does not say. */
@Composable
internal fun closedText(tag: ClosedTag): String {
    val at = tag.at ?: return pluginString(Strings.market_closed)
    val locale = Locale.forLanguageTag(currentLanguage())
    val pattern = if (tag.weekday) DateTimeFormatter.ofPattern("EEE", locale) else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return pluginString(Strings.closed_on, pattern.format(at.atZone(ZoneId.systemDefault())))
}

/** A section's last close: the date with its weekday, short, in the reader's language. */
@Composable
internal fun closeDateText(at: Instant): String {
    val locale = Locale.forLanguageTag(currentLanguage())
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(at.atZone(ZoneId.systemDefault())).let { date ->
        DateTimeFormatter.ofPattern("EEE", locale).format(at.atZone(ZoneId.systemDefault())) + ", " + date
    }
}

/** What a move is measured over: "24h" for a rolling day, "1D" against the last session's close. */
@Composable
internal fun windowText(window: ChangeWindow): String = pluginString(if (window == ChangeWindow.ROLLING_24H) Strings.window_24h else Strings.range_day)

/** The word for what an instrument is: Index, Stock, Crypto… */
internal fun kindWord(kind: TickerKind?): PluginText? = when (kind) {
    null -> null
    TickerKind.MARKET_INDEX -> Strings.type_index
    TickerKind.STOCK -> Strings.type_stock
    TickerKind.ETF -> Strings.type_etf
    TickerKind.CRYPTO -> Strings.type_crypto
    TickerKind.FUND -> Strings.type_fund
    TickerKind.FUTURE -> Strings.type_future
    TickerKind.CURRENCY -> Strings.type_currency
}

/** A row's second line: `$SPX · Index`, or the kind alone when the title already is the symbol. */
@Composable
internal fun subtitleText(cashtag: String?, kind: TickerKind?): String? {
    val word = kindWord(kind)?.let { pluginString(it) }
    return listOfNotNull(cashtag, word).joinToString(" · ").ifEmpty { null }
}
