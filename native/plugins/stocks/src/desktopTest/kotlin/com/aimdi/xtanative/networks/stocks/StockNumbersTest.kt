package com.aimdi.xtanative.networks.stocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockNumbersTest {
    private val de = NumberSymbols.forLocale("de")

    @Test
    fun movesAreSignedRoundedToFlatAndUseARealMinus() {
        assertEquals("+1.23%", StockNumbers.percent(1.234))
        assertEquals("−0.40%", StockNumbers.percent(-0.4))
        assertEquals("0.00%", StockNumbers.percent(-0.001))
        assertEquals("−2.50", StockNumbers.change(-2.5))
    }

    @Test
    fun pricesKeepUsefulDigitsAtEverySize() {
        assertEquals("1,234.50", StockNumbers.price(1234.5))
        assertEquals("0.3512", StockNumbers.price(0.35121))
        assertEquals("0.0₄1230", StockNumbers.price(0.0000123))
        assertEquals("0.00", StockNumbers.price(0.0))
    }

    @Test
    fun underOneUnitFourSignificantDigitsAndFromThreeZerosOnTheZerosAreCounted() {
        assertEquals("0.1234", StockNumbers.price(0.1234))
        assertEquals("0.01234", StockNumbers.price(0.01234))
        assertEquals("0.001810", StockNumbers.price(0.00181), "was 0.00181, three digits")
        assertEquals("0.0₃1234", StockNumbers.price(0.0001234))
        assertEquals("0.0₁₂1230", StockNumbers.price(0.000000000000123))
        assertEquals("0.0₇1230", StockNumbers.price(0.0000000123))
        assertEquals("0.1000", StockNumbers.price(0.099996), "rounded before the zeros are counted")
        assertEquals("1.00", StockNumbers.price(0.99996))
        assertEquals("−0.0₃1234", StockNumbers.change(-0.0001234))
    }

    @Test
    fun aScreenReaderHearsTheZerosWrittenOut() {
        assertEquals("0.0001234", StockNumbers.priceSpoken(0.0001234))
        assertEquals("0.001810", StockNumbers.priceSpoken(0.00181))
        assertEquals("1,234.50", StockNumbers.priceSpoken(1234.5))
    }

    @Test
    fun theReadersLocaleGroupsDigitsAndOneIntlLacksIsEnglish() {
        assertEquals("1.234,50", StockNumbers.price(1234.5, de))
        assertEquals("+1,50%", StockNumbers.percent(1.5, de))
        assertEquals("0,001810", StockNumbers.price(0.00181, de))
        assertEquals("0,0₃1234", StockNumbers.price(0.0001234, de))
        assertEquals("1,234.50", StockNumbers.price(1234.5, NumberSymbols.forLocale("eo")), "Esperanto has no number symbols in intl")
        assertEquals("12,34,567.89", StockNumbers.price(1234567.891, NumberSymbols.forLocale("hi")), "Hindi groups by two after the first three")
        assertEquals(NumberSymbols.forLocale("pt"), NumberSymbols.forLocale("pt-br").copy(), "pt_BR writes numbers as pt does")
        assertEquals(NumberSymbols.forLocale("zh"), NumberSymbols.forLocale("zh_Hant"))
    }

    @Test
    fun halvesRoundAwayFromZeroOnTheDoubleAsIntlRoundsThem() {
        assertEquals("12.35", StockNumbers.price(12.345))
        assertEquals("1.00", StockNumbers.price(1.005), "1.005 is 1.00499… as a double")
        assertEquals("100.13", StockNumbers.price(100.125))
        assertEquals("1,000.00", StockNumbers.price(999.995))
    }

    @Test
    fun volumesAndDollarFiguresAreCompactAsTheLanguageShortensThem() {
        assertEquals("48.2M", StockNumbers.compact(48_200_000.0))
        assertEquals("1M", StockNumbers.compact(999_999.0), "999999 is 1M, not 1000K")
        assertEquals("$1.23B", StockNumbers.compactUsd(1_234_567_890.0))
        assertEquals("$999", StockNumbers.compactUsd(999.0))
        assertEquals("$12.00", StockNumbers.compactUsd(12.0), "an amount too small to shorten keeps its cents")
        assertEquals("48,2 Mio.", StockNumbers.compact(48_200_000.0, de))
        assertEquals("999500", StockNumbers.compact(999_500.0, de), "German writes thousands out, ungrouped")
        assertEquals("3 آلاف", StockNumbers.compact(3000.0, NumberSymbols.forLocale("ar")), "Arabic's few form")
        assertEquals("3.45 ألف", StockNumbers.compact(3450.0, NumberSymbols.forLocale("ar")))
    }

    @Test
    fun aGainIsUpALossDownAndUnknownOrFlatIsNeutral() {
        assertEquals(Trend.UP, StockNumbers.trend(2.0))
        assertEquals(Trend.DOWN, StockNumbers.trend(-2.0))
        assertEquals(Trend.NEUTRAL, StockNumbers.trend(null), "green for no data would claim news nobody reported")
        assertEquals(Trend.NEUTRAL, StockNumbers.trend(0.001))
    }

    /** Every line of the golden table: XTA's own Dart formatting, run on intl 0.20.3, in all of XTA's languages. */
    @Test
    fun everyLanguageWritesTheFiguresAsXtaDoes() {
        val lines = fixture("number_goldens.tsv").lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val wrong = lines.mapNotNull { line ->
            val (kind, locale, input, expected) = line.split('\t')
            val symbols = NumberSymbols.forLocale(locale)
            val value = input.toDouble()
            val actual = when (kind) {
                "price" -> StockNumbers.price(value, symbols)
                "spoken" -> StockNumbers.priceSpoken(value, symbols)
                "percent" -> StockNumbers.percent(value, symbols)
                "change" -> StockNumbers.change(value, symbols)
                "compact" -> StockNumbers.compact(value, symbols)
                "compactUsd" -> StockNumbers.compactUsd(value, symbols)
                else -> error("unknown kind $kind")
            }
            if (actual == expected) null else "$kind $locale $input: expected [$expected], got [$actual]"
        }
        assertTrue(lines.size > 1000, "the table is there: ${lines.size} lines")
        assertTrue(wrong.isEmpty(), "${wrong.size} of ${lines.size} differ:\n" + wrong.take(40).joinToString("\n"))
    }
}
