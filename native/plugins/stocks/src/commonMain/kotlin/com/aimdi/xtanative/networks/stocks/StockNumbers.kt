package com.aimdi.xtanative.networks.stocks

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How a language writes numbers: its separators, minus sign and digit
 * groups (2 after the first 3 in Hindi: `12,34,567`), and the short
 * compact patterns by power of ten (`0K`, `0 Mio.`), with `¤` where a
 * currency's sign goes. [forLocale] has XTA's languages.
 */
data class NumberSymbols(
    val decimal: String,
    val group: String,
    val minus: String = "-",
    val zero: Char = '0',
    val primaryGrouping: Int = 3,
    val secondaryGrouping: Int = 3,
    val compact: Map<Int, String> = emptyMap(),
    val compactCurrency: Map<Int, String> = emptyMap(),
    /** The positive currency pattern, for an amount too small to abbreviate: `¤#,##0.00`. */
    val currencyPattern: String = "¤#,##0.00",
    /**
     * The compact patterns for a "few" count, where they differ: Arabic, the
     * one XTA language whose short patterns have plural forms, writes 3 to 10
     * thousand `آلاف`, not `ألف`.
     */
    val compactFew: Map<Int, String> = emptyMap(),
    val compactCurrencyFew: Map<Int, String> = emptyMap(),
) {
    companion object {
        val EN: NumberSymbols by lazy { forLocale("en") }

        /**
         * [locale] (`de`, `pt_BR`, `zh-Hant`) as XTA formats numbers in it,
         * resolved as intl resolves it: the locale, its canonical spelling,
         * its language, else English (Esperanto, which intl lacks).
         */
        fun forLocale(locale: String): NumberSymbols {
            val known = NumberLocales.BY_LOCALE
            val tag = locale.replace('-', '_')
            val region = if (tag.length >= 5 && tag[2] == '_') tag.substring(3) else ""
            val canonical = if (region.isNotEmpty() && region.length <= 3) "${tag.take(2)}_${region.uppercase()}" else tag
            return known[tag] ?: known[canonical] ?: known[tag.take(2).lowercase()] ?: known.getValue("en")
        }
    }
}

/** Which way a move went, as its colour says: green, red, or neutral for unknown or flat (no colour claims news nobody reported). */
enum class Trend { UP, DOWN, NEUTRAL }

/**
 * The number rules of XTA's stocks_format.dart, as text without the screen:
 * prices that stay readable at any size, signed moves with a real minus,
 * compact volumes and dollar figures, each in the reader's [NumberSymbols].
 */
object StockNumbers {
    /** Stands in for a number that has not arrived: punctuation, so it needs no translation. */
    const val PLACEHOLDER = "—"

    /** A move this small prints as 0.00% and is drawn as flat, not as a gain. */
    const val FLAT_PERCENT = 0.005

    private const val SIGNIFICANT = 4
    private const val COUNTED_ZEROS = 3
    private const val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"
    private val countedZeros = Regex("0([$SUBSCRIPTS]+)")

    /**
     * A price: two decimals from one unit up; below it four significant
     * digits (`0.1234`, `0.001810`), and from three zeros after the separator
     * on those zeros counted (`0.0₃1234`), so a token worth a fraction of a
     * cent stays readable. A screen reader gets [priceSpoken].
     */
    fun price(value: Double, symbols: NumberSymbols = NumberSymbols.EN): String {
        val small = small(value) ?: return decimal(value, 2, symbols)
        if (small.zeros < COUNTED_ZEROS || symbols.zero != '0') {
            val decimals = small.zeros + SIGNIFICANT
            return if (decimals > 15) exponential(value) else decimal(value, decimals, symbols)
        }
        return "${if (value < 0) "-" else ""}0${symbols.decimal}0${subscript(small.zeros)}${small.digits}"
    }

    /** [shown] as it is read aloud: counted zeros written out (`0.0₃1234` is `0.0001234`), anything else as it is. */
    fun spoken(shown: String): String = countedZeros.replace(shown) { match -> "0".repeat(subscriptValue(match.groupValues[1])) }

    /** [price] as a screen reader should say it. */
    fun priceSpoken(value: Double, symbols: NumberSymbols = NumberSymbols.EN): String = spoken(price(value, symbols))

    /** `+1.23%`, `−0.40%`, `0.00%`: signed with a real minus (it lines up with `+`), two decimals, flat under [FLAT_PERCENT]. */
    fun percent(value: Double, symbols: NumberSymbols = NumberSymbols.EN): String {
        val rounded = if (abs(value) < FLAT_PERCENT) 0.0 else value
        return signed(decimal(abs(rounded), 2, symbols), rounded) + "%"
    }

    /** `+1.23`, `−0.0₃1234`: the absolute move, at the precision of its price. */
    fun change(value: Double, symbols: NumberSymbols = NumberSymbols.EN): String = signed(price(abs(value), symbols), value)

    /** Up, down, or neutral for an unknown or flat move. */
    fun trend(percent: Double?): Trend = when {
        percent == null || abs(percent) < FLAT_PERCENT -> Trend.NEUTRAL
        percent > 0 -> Trend.UP
        else -> Trend.DOWN
    }

    /** `48.2M`: volume and supply, shortened as the reader's language shortens them, three significant digits. */
    fun compact(value: Double, symbols: NumberSymbols = NumberSymbols.EN): String = compactOf(value, symbols, currency = false)

    /** `$1.23B`: market cap, liquidity and dollar volume; an amount too small to shorten is written in full, cents and all. */
    fun compactUsd(value: Double, symbols: NumberSymbols = NumberSymbols.EN): String = compactOf(value, symbols, currency = true)

    /** [value] with [digits] decimals in [symbols], grouped, rounded half away from zero as intl rounds. */
    private fun decimal(value: Double, digits: Int, symbols: NumberSymbols): String {
        if (value.isNaN()) return "NaN"
        val sign = if (value < 0 || (value == 0.0 && 1 / value < 0)) symbols.minus else ""
        if (value.isInfinite()) return "$sign∞"
        val magnitude = abs(value)
        var integer = floor(magnitude)
        val power = 10.0.pow(digits)
        var rest = Math.round((magnitude - integer) * power)
        if (rest >= power) {
            integer += 1
            rest -= power.toLong()
        }
        val fraction = if (digits > 0) symbols.decimal + rest.toString().padStart(digits, '0') else ""
        return sign + localized(group(wholeDigits(integer), symbols) + fraction, symbols)
    }

    private class Small(val zeros: Int, val digits: String)

    /** A price under one unit as its zeros after the separator and its [SIGNIFICANT] digits, after rounding. */
    private fun small(price: Double): Small? {
        val magnitude = abs(price)
        if (magnitude == 0.0 || !magnitude.isFinite() || magnitude >= 1) return null
        val (digits, power) = scientific(magnitude)
        return if (power >= 0) null else Small(-power - 1, digits)
    }

    /** [magnitude]'s [SIGNIFICANT] digits and its power of ten, correctly rounded half up as Dart's toStringAsExponential rounds. */
    private fun scientific(magnitude: Double): Pair<String, Int> {
        val rounded = BigDecimal(magnitude).round(MathContext(SIGNIFICANT, RoundingMode.HALF_UP))
        return rounded.unscaledValue().abs().toString().padEnd(SIGNIFICANT, '0') to rounded.precision() - rounded.scale() - 1
    }

    /** `1.230e-13`, as Dart writes a number with three decimals in scientific notation. */
    private fun exponential(value: Double): String {
        val (digits, power) = scientific(abs(value))
        val sign = if (value < 0) "-" else ""
        return "$sign${digits[0]}.${digits.drop(1)}e${if (power < 0) "-" else "+"}${abs(power)}"
    }

    private fun subscript(count: Int): String = count.toString().map { SUBSCRIPTS[it - '0'] }.joinToString("")

    private fun subscriptValue(digits: String): Int = digits.map { SUBSCRIPTS.indexOf(it) }.joinToString("").toInt()

    private fun signed(digits: String, value: Double): String = when {
        value > 0 -> "+$digits"
        value < 0 -> "−$digits"
        else -> digits
    }

    private fun wholeDigits(integer: Double): String = BigDecimal(integer).toBigInteger().toString()

    private fun group(digits: String, symbols: NumberSymbols): String {
        if (digits.length <= symbols.primaryGrouping) return digits
        val head = digits.dropLast(symbols.primaryGrouping).reversed().chunked(symbols.secondaryGrouping).map(String::reversed).reversed()
        return (head + digits.takeLast(symbols.primaryGrouping)).joinToString(symbols.group)
    }

    private fun localized(text: String, symbols: NumberSymbols): String =
        if (symbols.zero == '0') text else text.map { if (it in '0'..'9') symbols.zero + (it - '0') else it }.joinToString("")

    // ---- compact, as intl's compact formats work with three significant digits ----

    /** A compact pattern taken apart; [exponent] is the power of ten it is filed under, null for a number written out. */
    private class CompactStyle(val prefix: String, val suffix: String, val divisor: Double, val fallback: Boolean, val exponent: Int? = null)

    private val unabbreviated = CompactStyle("", "", 1.0, fallback = true)
    private val affixes = Regex("([^0]*)(0+)(.*)")
    private val onlyZeros = Regex("^0*$")

    private fun compactOf(value: Double, symbols: NumberSymbols, currency: Boolean): String {
        if (value.isNaN()) return "NaN"
        if (value < 0) return symbols.minus + compactOf(-value, symbols, currency)
        if (value.isInfinite()) return "∞"
        val chosen = styleFor(value, if (currency) symbols.compactCurrency else symbols.compact)
        val n = if (chosen.fallback) value else value / chosen.divisor
        val style = fewForm(chosen, n, if (currency) symbols.compactCurrencyFew else symbols.compactFew) ?: chosen
        val digits = significant(n, currency && style.fallback, symbols)
        if (!style.fallback) return (style.prefix + digits + style.suffix).replaceFirst("¤", "$")
        if (!currency) return digits
        val pattern = symbols.currencyPattern
        val start = pattern.indexOfFirst { it == '#' || it == '0' }.coerceAtLeast(0)
        val end = pattern.indexOfLast { it == '#' || it == '0' }
        return (pattern.substring(0, start) + digits + pattern.substring(end + 1)).replace("¤", "$")
    }

    /** The pattern for [value]'s magnitude, chosen on the value rounded to three significant digits, so 999999 is 1M, not 1000K. */
    private fun styleFor(value: Double, patterns: Map<Int, String>): CompactStyle {
        if (value < 10) return unabbreviated
        var rounded = value
        var digitLength = integerDigits(value)
        var divisor = 1.0
        fun round() {
            val multiplier = 10.0.pow(max(0, 3 - digitLength + integerDigits(divisor) - 1))
            rounded = Math.round(rounded * multiplier / divisor) * divisor / multiplier
            digitLength = integerDigits(rounded)
        }
        round()
        var style: CompactStyle? = null
        for ((exponent, pattern) in patterns.toSortedMap()) {
            if (exponent + 1 > digitLength) break
            val next = styleOf(pattern, exponent)
            style = next
            divisor = next.divisor
            round()
        }
        return style ?: unabbreviated
    }

    private fun styleOf(pattern: String, exponent: Int): CompactStyle {
        val match = affixes.find(pattern) ?: return unabbreviated
        val (prefix, zeros, suffix) = match.destructured
        val divisor = if (onlyZeros.matches(pattern)) 1.0 else 10.0.pow(exponent - zeros.length + 1)
        return CompactStyle(prefix, suffix, divisor, fallback = pattern == "0", exponent = exponent)
    }

    /**
     * The "few" form of [style] for [n] shown with three significant digits,
     * when [few] has one: Arabic's, for a whole count whose last two digits
     * are 3 to 10 (`3 آلاف`, but `3.45 ألف`).
     */
    private fun fewForm(style: CompactStyle, n: Double, few: Map<Int, String>): CompactStyle? {
        val exponent = style.exponent ?: return null
        val pattern = few[exponent] ?: return null
        val factor = 10.0.pow(max(0, 3 - integerDigits(floor(n))))
        val shown = Math.round(n * factor) / factor
        return if (shown == floor(shown) && shown % 100 in 3.0..10.0) styleOf(pattern, exponent) else null
    }

    /**
     * [n] with three significant digits and no grouping, trailing zeros
     * dropped; an unabbreviated currency amount ([cents]) keeps two decimals
     * while it has digits to spare, as intl does for dollars.
     */
    private fun significant(n: Double, cents: Boolean, symbols: NumberSymbols): String {
        var integer = floor(n)
        var fraction = n - integer
        var digits = fractionDigits(n, integer, fraction, cents)
        // The rounding below works at the first count of digits; intl counts them again after it, at that same power.
        val power = 10.0.pow(digits)
        var rest = Math.round(fraction * power)
        if (rest >= power) {
            integer += 1
            rest -= power.toLong()
            digits = fractionDigits(n, integer, fraction, cents)
        } else if (integerDigits(rest.toDouble()) > integerDigits(floor(fraction * power))) {
            fraction = rest / power
            digits = fractionDigits(n, integer, fraction, cents)
        }
        val minFraction = if (cents) 2 else 0
        val shown = digits > 0 && (minFraction > 0 || rest > 0)
        val fractionText = if (!shown) "" else symbols.decimal + trimZeros((rest + power.toLong()).toString().drop(1), minFraction)
        return localized(wholeDigits(integer) + fractionText, symbols)
    }

    /** The decimals three significant digits leave for [n] (whole part [integer]); dollars too small to shorten keep their two. */
    private fun fractionDigits(n: Double, integer: Double, fraction: Double, cents: Boolean): Int {
        val integerLength = when {
            n == 0.0 -> 1
            integer != 0.0 -> integerDigits(integer)
            else -> ceil(log10(fraction)).toInt()
        }
        val remaining = 3 - integerLength
        val digits = max(0, remaining)
        return if (!cents) digits else if (remaining > 0) 2 else min(digits, 2)
    }

    private fun trimZeros(fraction: String, keep: Int): String {
        var length = fraction.length
        while (length > keep && fraction[length - 1] == '0') length--
        return fraction.take(length)
    }

    /** How many digits [value] has before its decimal separator, as intl counts them (at least one). */
    private fun integerDigits(value: Double): Int {
        val magnitude = abs(value)
        var digits = 1
        var bound = 10.0
        while (digits < 19 && magnitude >= bound) {
            digits++
            bound *= 10
        }
        return digits
    }
}
