package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteRowsTest {
    private val now: Instant = Instant.parse("2026-11-02T15:00:00Z")
    private val pepe = CryptoAsset("ethereum", "0x6982508145454ce325ddbe47a25d4ec3d2311933", "PEPE", "Pepe")

    /** A share whose regular session is happening now. */
    private val liveShare = TickerQuote(
        symbol = "AAPL",
        currency = "USD",
        price = 200.0,
        previousClose = 190.0,
        points = listOf(TickerPoint(now.minusSeconds(600), 199.0), TickerPoint(now.minusSeconds(60), 200.0)),
        shortName = "Apple Inc.",
        instrumentType = "EQUITY",
        regularPeriod = TradingPeriod(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1))),
    )

    /** The Nasdaq-100 with its exchange shut: the last close three days ago, no bars. */
    private val lastClose: Instant = now.minus(Duration.ofDays(3))
    private val closedIndex = TickerQuote(
        symbol = "^NDX",
        currency = "USD",
        price = 24172.03,
        previousClose = 24063.4,
        points = emptyList(),
        instrumentType = "INDEX",
        regularMarketTime = lastClose,
        regularPeriod = TradingPeriod(lastClose.minus(Duration.ofHours(6)), lastClose),
    )

    private val token = CryptoMarket(pepe, price = 0.0000123, changePercent = -4.2).quote

    @Test
    fun nothingYetIsLoadingAFailedLookupOrAQuoteWithoutAPriceIsNoData() {
        assertEquals(StockRowState.LOADING, QuoteRows.state(null, failed = false, now = now))
        assertEquals(StockRowState.NO_DATA, QuoteRows.state(null, failed = true, now = now))
        assertEquals(StockRowState.NO_DATA, QuoteRows.state(CryptoMarket(pepe).quote, failed = false, now = now))
        assertEquals(StockRowState.LIVE, QuoteRows.state(liveShare, failed = true, now = now), "an answer beats a failure")
    }

    @Test
    fun outsideEverySessionTheMarketIsClosedACoinNeverIs() {
        assertEquals(StockRowState.CLOSED, QuoteRows.state(closedIndex, failed = false, now = now))
        assertEquals(StockRowState.LIVE, QuoteRows.state(liveShare, failed = false, now = now))
        assertEquals(StockRowState.LIVE, QuoteRows.state(token, failed = false, now = now))
    }

    @Test
    fun aClosedMarketShowsTheWeekdayOfItsCloseOrItsDateOnceAWeekOld() {
        assertEquals(ClosedTag(lastClose, weekday = true), QuoteRows.closedTag(closedIndex, now))
        assertEquals(ClosedTag(lastClose, weekday = false), QuoteRows.closedTag(closedIndex, lastClose.plus(Duration.ofDays(6))))
        val barsOnly = closedIndex.copy(regularMarketTime = null, points = listOf(TickerPoint(lastClose, 1.0)))
        assertEquals(lastClose, QuoteRows.closedTag(barsOnly, now).at, "else its last bar's")
        assertNull(QuoteRows.closedTag(closedIndex.copy(regularMarketTime = null), now).at, "\"Market closed\" when the quote does not say")
    }

    @Test
    fun aRowSaysWhatItIsAndAPickedListingGoesByTheQuotesNameAlone() {
        assertEquals(TickerKind.MARKET_INDEX, QuoteRows.kindOf("SPX", null), "known before any data")
        assertEquals(TickerKind.STOCK, QuoteRows.kindOf("AAPL", liveShare))
        assertNull(QuoteRows.kindOf("AAPL", null))
        assertEquals(TickerKind.STOCK, QuoteRows.kindOf("BTC", null, TickerInstrument.STOCK))
        val fund = liveShare.copy(shortName = "Bitcoin Trust", instrumentType = "ETF")
        assertEquals("Bitcoin", QuoteRows.name("BTC", fund))
        assertEquals("Bitcoin Trust", QuoteRows.name("BTC", fund, TickerInstrument.STOCK), "\$BTC read as the listed fund is not Bitcoin")
    }

    @Test
    fun marketsRowsAreNamedBeforeAnyPriceOverTheirCashtag() {
        assertEquals(RowIdentity("S&P 500", cashtag = "\$SPX", kind = TickerKind.MARKET_INDEX), QuoteRows.marketsRow("SPX", null))
        assertEquals(RowIdentity("Nasdaq-100", cashtag = "\$NDX", kind = TickerKind.MARKET_INDEX), QuoteRows.marketsRow("NDX", closedIndex))
        assertEquals(listOf("SPX", "NDX", "DJI", "RUT", "VIX", "DAX", "FTSE", "N225"), QuoteRows.INDICES)
        assertEquals(listOf("BTC", "ETH"), QuoteRows.COINS)
    }

    @Test
    fun aWatchlistRowIsATokenByItsSymbolAndNetworkOrATickerByItsName() {
        assertEquals(RowIdentity("PEPE", network = "Ethereum", shortAddress = "0x6982…1933"), QuoteRows.watchlistRow(pepe.id, token, pepe))
        assertEquals(RowIdentity("Apple Inc.", cashtag = "\$AAPL", kind = TickerKind.STOCK), QuoteRows.watchlistRow("AAPL", liveShare, null))
        assertEquals(RowIdentity("POD"), QuoteRows.watchlistRow("POD", null, null), "the symbol alone, over nothing until a kind is known")
        assertEquals(RowIdentity("PEPE", network = "Ethereum"), QuoteRows.stripEntry(pepe.id, pepe))
        assertEquals(RowIdentity("SPX"), QuoteRows.stripEntry("^GSPC", null))
    }

    @Test
    fun aSectionSaysWhetherItsMarketsTrade() {
        assertEquals(StocksSection(StocksSectionKind.UNKNOWN), QuoteRows.section(listOf(null, CryptoMarket(pepe).quote), now))
        assertEquals(StocksSection(StocksSectionKind.LIVE_24H), QuoteRows.section(listOf(token, null), now))
        assertEquals(StocksSection(StocksSectionKind.LIVE_DAY), QuoteRows.section(listOf(closedIndex, liveShare), now))
        val older = closedIndex.copy(regularMarketTime = lastClose.minus(Duration.ofDays(1)))
        assertEquals(StocksSection(StocksSectionKind.CLOSED, lastClose), QuoteRows.section(listOf(older, closedIndex, null), now), "with the newest close")
    }

    @Test
    fun aCoinFirstCashtagWithAListingOffersBothReadingsTheCoinFirst() {
        val listing = YahooFinance.quote(
            J.parse("""{"chart":{"result":[{"meta":{"symbol":"SOL","instrumentType":"EQUITY","shortName":"Emeren Group Ltd","regularMarketPrice":1.9}}]}}"""),
            "SOL",
        )
        val readings = QuoteRows.readings("SOL", listing, TickerInstrument.AUTO, shown = null)
        assertEquals(
            listOf(
                StocksReading(TickerInstrument.CRYPTO, "SOL", TickerKind.CRYPTO),
                StocksReading(TickerInstrument.STOCK, "Emeren Group Ltd", TickerKind.STOCK),
            ),
            readings,
        )
        assertEquals("Ethereum", QuoteRows.readings("ETH", listing, TickerInstrument.AUTO, shown = null)!!.first().name, "the coin's own name")
        assertNull(QuoteRows.readings("SOL", listing, TickerInstrument.STOCK, shown = null), "nothing left to choose")
        assertNull(QuoteRows.readings("SOL", null, TickerInstrument.AUTO, shown = null), "one reading only")
    }
}
