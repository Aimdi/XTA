package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CoinSourcesTest {
    private val start: Instant = Instant.parse("2026-10-30T00:00:00Z")

    /** [count] bars [step] apart from [start], each closing one higher than the last and opening half a unit under its close. */
    private fun bars(count: Int, step: Duration) = (0 until count).map { i ->
        CoinBar(start.plus(step.multipliedBy(i.toLong())), open = 99.5 + i, high = 101.0 + i, low = 99.0 + i, close = 100.0 + i)
    }

    private val unused = FakeHttp { answer("", 500) }

    @Test
    fun chartWindowsReadTheRangesTheAppAsksFor() {
        assertEquals(Duration.ofDays(1), TickerRange.DAY.window.span)
        assertEquals(Duration.ofMinutes(5), TickerRange.DAY.window.step)
        assertEquals(Duration.ofDays(5), TickerRange.WEEK.window.span)
        assertEquals(Duration.ofMinutes(30), TickerRange.WEEK.window.step)
        assertEquals(Duration.ofDays(30), TickerRange.MONTH.window.span)
        assertEquals(Duration.ofDays(180), TickerRange.SIX_MONTHS.window.span)
        assertEquals(Duration.ofDays(365), TickerRange.YEAR.window.span)
        assertEquals(Duration.ofDays(7), TickerRange.FIVE_YEARS.window.step)
        assertNull(ChartWindow.parse("max", "1d"))
    }

    @Test
    fun eachExchangeAnswersOnlyWhatItCoversInOneAnswer() {
        val coinbase = CoinbaseSource(unused)
        assertEquals(Duration.ofMinutes(5), coinbase.stepFor(TickerRange.DAY.window))
        assertEquals(Duration.ofHours(1), coinbase.stepFor(TickerRange.WEEK.window), "no 30-minute candles")
        assertEquals(Duration.ofDays(1), coinbase.stepFor(TickerRange.SIX_MONTHS.window))
        assertNull(coinbase.stepFor(TickerRange.YEAR.window), "365 days are more than 300 candles")
        assertEquals(Duration.ofMinutes(30), KrakenSource(unused).stepFor(TickerRange.WEEK.window))
        assertEquals(Duration.ofDays(7), KrakenSource(unused).stepFor(TickerRange.FIVE_YEARS.window))
        assertEquals(Duration.ofDays(7), BinanceSource(unused).stepFor(TickerRange.FIVE_YEARS.window))
    }

    @Test
    fun coinbaseIsAskedForTheUsdProductInSecondsNewestCandlesFirst() {
        assertEquals("https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=300", CoinbaseSource.candlesUrl("BTC", Duration.ofMinutes(5)).toString())
        val quote = CoinBars.quote("BTC-USD", CoinBars.coinbase(J.parse(fixture("coinbase_candles.json"))), Duration.ofDays(1), "USD")
        assertEquals(listOf(100.0, 101.0, 102.0), quote.points.map { it.close })
        assertEquals(102.0, quote.price)
        assertEquals(99.5, quote.previousClose, "the open of the first bar, where the chart began")
        assertTrue(quote.tradesAroundTheClock)
        assertEquals("Bitcoin", quote.shortName)
        assertEquals(TickerKind.CRYPTO, quote.kind)
    }

    @Test
    fun krakenIsAskedUnderItsOwnNamesAndAnUnknownPairIsNotFound() {
        val url = KrakenSource.ohlcUrl("BTC", Duration.ofMinutes(30))
        assertEquals("XBTUSD", url.queryParameter("pair"))
        assertEquals("30", url.queryParameter("interval"))
        assertEquals("XDGUSD", KrakenSource.ohlcUrl("DOGE", Duration.ofDays(1)).queryParameter("pair"))
        assertEquals("SOLUSD", KrakenSource.ohlcUrl("SOL", Duration.ofDays(1)).queryParameter("pair"))
        assertEquals(listOf(100.0, 101.0), CoinBars.kraken(J.parse(fixture("kraken_ohlc.json"))).map { it.close })
        val unknown = assertFailsWith<TickerException> { CoinBars.kraken(j(mapOf("error" to listOf("EQuery:Unknown asset pair")))) }
        assertEquals(TickerErrorKind.NOT_FOUND, unknown.kind)
        val busy = assertFailsWith<TickerException> { CoinBars.kraken(j(mapOf("error" to listOf("EGeneral:Too many requests")))) }
        assertEquals(TickerErrorKind.UNAVAILABLE, busy.kind)
    }

    @Test
    fun binanceIsAskedForTheUsdtPairWithAsManyKlinesAsTheSpanNeeds() {
        val url = BinanceSource.klinesUrl("ETH", Duration.ofMinutes(30), TickerRange.WEEK.window)
        assertEquals("ETHUSDT", url.queryParameter("symbol"))
        assertEquals("30m", url.queryParameter("interval"))
        assertEquals("241", url.queryParameter("limit"))
        assertEquals(listOf(100.0, 101.0), CoinBars.binance(J.parse(fixture("binance_klines.json"))).map { it.close })
    }

    @Test
    fun aChartKeepsItsSpanAndTheDayItsFiguresSinceMidnightUtc() {
        val bars = bars(5 * 48, Duration.ofMinutes(30))
        val quote = CoinBars.quote("ETH-USD", bars, Duration.ofDays(5), "USD")
        assertEquals(bars.first().at, quote.points.first().at)
        val day = quote.rolling24h()
        assertEquals(ChangeWindow.ROLLING_24H, day.changeWindow)
        assertEquals(bars[bars.size - 49].close, day.previousClose, "the close 24 hours before the last")
        val midnight = bars.indexOfFirst { it.at == Instant.parse("2026-11-03T00:00:00Z") }
        assertEquals(bars[midnight].open, quote.dayOpen)
        assertEquals(bars.last().high, quote.dayHigh)
        assertEquals(bars[midnight].low, quote.dayLow)
    }

    @Test
    fun aDaysChartStartsAtMidnightUtcAgainstTheCloseBeforeItAsOnYahoo() {
        val bars = bars(48, Duration.ofHours(1))
        val quote = CoinBars.quote("BTC-USD", bars.shuffled(), Duration.ofDays(1), "USD")
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), quote.points.first().at)
        assertEquals(24, quote.points.size)
        assertEquals(bars[23].close, quote.previousClose, "the last close before midnight")
        assertEquals(ChangeWindow.PREVIOUS_CLOSE, quote.changeWindow)
        assertEquals(TickerKind.CRYPTO, quote.kind)
    }

    @Test
    fun noTradesIsNoSuchCoin() {
        assertEquals(TickerErrorKind.NOT_FOUND, assertFailsWith<TickerException> { CoinBars.quote("NOPE-USD", emptyList(), Duration.ofDays(1), "USD") }.kind)
    }

    @Test
    fun aCoinPairIsYahoosSpellingOfACoin() {
        assertTrue(CoinSourceChain.isCoinPair("BTC-USD"))
        assertFalse(CoinSourceChain.isCoinPair("-USD"))
        assertFalse(CoinSourceChain.isCoinPair("^GSPC"))
        assertFalse(CoinSourceChain.isCoinPair("AAPL"))
    }

    /** A source that answers from [answer] and remembers what it was asked. */
    private class Source(override val name: String, private val step: Duration? = Duration.ofMinutes(30), private val answer: (String) -> TickerQuote) : CoinSource {
        val asked = mutableListOf<String>()

        override fun stepFor(window: ChartWindow): Duration? = step

        override suspend fun chart(coin: String, window: ChartWindow): TickerQuote {
            asked.add(coin)
            return answer(coin)
        }
    }

    private fun quoteFrom(source: String) = TickerQuote(symbol = "BTC-USD", currency = source, price = 1.0, previousClose = 1.0, points = emptyList())

    private val refuse: (String) -> TickerQuote = { throw NetworkRequestException("test", 429, "429") }
    private val unknown: (String) -> TickerQuote = { throw TickerException(TickerErrorKind.NOT_FOUND, "no pair") }

    private class Clock(var now: Instant) {
        fun advance(by: Duration) {
            now = now.plus(by)
        }
    }

    private val clock = Clock(Instant.parse("2026-11-01T12:00:00Z"))
    private val health = PriceSourceHealth { clock.now }

    private fun ask(chain: CoinSourceChain, pair: String = "BTC-USD", yahoo: suspend () -> TickerQuote = { quoteFrom("yahoo") }) =
        runBlocking { chain.chart(pair, TickerRange.WEEK.window, yahoo) }

    @Test
    fun theFirstSourceThatAnswersWinsInOrder() {
        val first = Source("first") { quoteFrom("first") }
        val second = Source("second") { quoteFrom("second") }
        assertEquals("first", ask(CoinSourceChain(listOf(first, second), health)).currency)
        assertTrue(second.asked.isEmpty())
    }

    @Test
    fun aRefusalRestsTheSourceTwiceAsLongEachTimeUntilItAnswers() {
        var refusing = true
        val first = Source("first") { if (refusing) refuse(it) else quoteFrom("first") }
        val second = Source("second") { quoteFrom("second") }
        val chain = CoinSourceChain(listOf(first, second), health)

        assertEquals("second", ask(chain).currency)
        assertEquals("second", ask(chain).currency)
        assertEquals(1, first.asked.size, "resting: not asked again")

        clock.advance(PriceSourceHealth.FIRST_REST)
        assertEquals("second", ask(chain).currency)
        assertEquals(2, first.asked.size)
        clock.advance(PriceSourceHealth.FIRST_REST)
        ask(chain)
        assertEquals(2, first.asked.size, "the second rest is twice as long")

        clock.advance(PriceSourceHealth.FIRST_REST.multipliedBy(2))
        refusing = false
        assertEquals("first", ask(chain).currency)
        assertFalse(health.resting("first"))
    }

    @Test
    fun restsDoubleUpToHalfAnHour() {
        val rests = (1..7).map {
            health.refused("busy")
            var minutes = 0L
            while (health.resting("busy")) {
                clock.advance(Duration.ofMinutes(1))
                minutes++
            }
            minutes
        }
        assertEquals(listOf(2L, 4, 8, 16, 30, 30, 30), rests)
    }

    @Test
    fun anAnswerThatIsNotTheSourcesFormatRestsIt() {
        val garbled = Source("garbled") { throw TickerException(TickerErrorKind.BAD_RESPONSE, "html") }
        val second = Source("second") { quoteFrom("second") }
        assertEquals("second", ask(CoinSourceChain(listOf(garbled, second), health)).currency)
        assertTrue(health.resting("garbled"))
    }

    @Test
    fun aCoinASourceDoesNotListIsNotAskedOfItAgainOtherCoinsAre() {
        val first = Source("first") { coin -> if (coin == "PEPE") unknown(coin) else quoteFrom("first") }
        val second = Source("second") { quoteFrom("second") }
        val chain = CoinSourceChain(listOf(first, second), health)
        ask(chain, pair = "PEPE-USD")
        ask(chain, pair = "PEPE-USD")
        assertEquals(listOf("PEPE"), first.asked)
        assertEquals("first", ask(chain).currency)
        assertFalse(health.resting("first"))
    }

    @Test
    fun aSourceThatCannotCoverTheChartIsSkippedWithoutResting() {
        val short = Source("short", step = null) { quoteFrom("short") }
        assertEquals("yahoo", ask(CoinSourceChain(listOf(short), health)).currency)
        assertTrue(short.asked.isEmpty())
        assertFalse(health.resting("short"))
    }

    @Test
    fun onlyWhenEveryoneSaysThereIsNoSuchCoinIsItNotFound() {
        val yahooUnknown: suspend () -> TickerQuote = { unknown("") }
        val notFound = assertFailsWith<TickerException> { ask(CoinSourceChain(listOf(Source("a", answer = unknown), Source("b", answer = unknown)), health), "NOPE-USD", yahooUnknown) }
        assertEquals(TickerErrorKind.NOT_FOUND, notFound.kind)
        val refused = assertFailsWith<TickerException> { ask(CoinSourceChain(listOf(Source("c", answer = refuse)), health), "NOPE-USD", yahooUnknown) }
        assertEquals(TickerErrorKind.UNAVAILABLE, refused.kind, "a refusal says nothing about whether the coin exists")
    }
}
