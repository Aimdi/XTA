package com.aimdi.xtanative.networks.stocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TickersTest {
    @Test
    fun theCashtagPeopleWriteForWhateverTheHostCallsTheSymbol() {
        assertEquals("AAPL", Tickers.spokenCashtag("aapl"), "an ordinary share stays as written")
        assertEquals("SPX", Tickers.spokenCashtag("^GSPC"))
        assertEquals("DAX", Tickers.spokenCashtag("^GDAXI"))
        assertEquals("BTC", Tickers.spokenCashtag("BTC-USD"), "a pair drops the quote currency")
        assertEquals("ETH", Tickers.spokenCashtag("eth-usd"))
    }

    @Test
    fun anOrdinaryShareIsAskedForAsWrittenThenAsAPair() {
        assertEquals(listOf("AAPL", "AAPL-USD"), Tickers.candidates("aapl"))
        assertEquals(listOf("BTC-USD"), Tickers.candidates("BTC-USD"), "a name that is already a pair is not mangled")
        assertEquals(listOf("^GSPC"), Tickers.candidates("^GSPC"))
    }

    @Test
    fun anIndexNameAsksForItsIndexAndNothingElse() {
        // $NDX once fell through to NDX-USD, a token, shown as the Nasdaq-100.
        for ((spoken, index) in Tickers.ALIASES) {
            for (prefer in TickerInstrument.entries) {
                assertEquals(listOf(index), Tickers.candidates(spoken.lowercase(), prefer), "$spoken, $prefer")
            }
        }
        assertEquals(listOf("^NDX"), Tickers.candidates("NDX"))
    }

    @Test
    fun aMajorCoinIsAskedForAsTheCoinFirstAndTheReaderCanInsistOnEither() {
        assertEquals(listOf("ETH-USD", "ETH"), Tickers.candidates("eth"))
        assertEquals(listOf("BTC-USD", "BTC"), Tickers.candidates("BTC"))
        assertEquals(listOf("ETH"), Tickers.candidates("ETH", TickerInstrument.STOCK))
        assertEquals(listOf("ETH-USD"), Tickers.candidates("ETH", TickerInstrument.CRYPTO))
        assertTrue(Tickers.isAmbiguous("sol"))
        assertFalse(Tickers.isAmbiguous("AAPL"))
    }

    @Test
    fun whatASymbolIsBeforeAnythingIsAsked() {
        for (symbol in listOf("SPX", "ndx", "DAX", "N225", "^GSPC")) assertEquals(TickerKind.MARKET_INDEX, Tickers.knownKind(symbol), symbol)
        for (symbol in listOf("BTC", "eth", "SHIB-USD")) assertEquals(TickerKind.CRYPTO, Tickers.knownKind(symbol), symbol)
        assertNull(Tickers.knownKind("AAPL"))
        assertNull(Tickers.knownKind("POD"))
    }

    @Test
    fun indicesAndTheTapeCoinsHaveNamesWithoutAnyData() {
        assertEquals(
            listOf("S&P 500", "Nasdaq-100", "Dow Jones", "Russell 2000", "VIX", "DAX 40", "FTSE 100", "Nikkei 225", "Bitcoin", "Ethereum"),
            Tickers.MARKET_SYMBOLS.map(Tickers::name),
        )
        assertEquals("S&P 500", Tickers.name("^GSPC"), "the host's spelling too")
        assertNull(Tickers.name("AAPL"))
    }

    @Test
    fun theTrendTabShowsTheFirstSixteenAsCashtags() {
        val trending = listOf("NVDA", "BTC-USD", "^GSPC") + (1..20).map { "T$it" }
        val shown = Tickers.trendingCashtags(trending)
        assertEquals(Tickers.TRENDING_SHOWN, shown.size)
        assertEquals(listOf("NVDA", "BTC", "SPX"), shown.take(3))
    }

    @Test
    fun thePicksAreKeptAsXtaKeepsThem() {
        val picks = TickerPicks.decode(listOf("sol=stock", "ETH=crypto", "BTC=auto", "junk", "A=b=c", "DOT=nonsense"))
        assertEquals(mapOf("SOL" to TickerInstrument.STOCK, "ETH" to TickerInstrument.CRYPTO), picks)
        assertEquals(listOf("SOL=stock", "ETH=crypto"), TickerPicks.encode(picks))
    }
}
