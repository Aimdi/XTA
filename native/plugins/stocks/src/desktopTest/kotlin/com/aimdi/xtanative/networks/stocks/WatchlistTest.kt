package com.aimdi.xtanative.networks.stocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WatchlistTest {
    private val a = "0x1111111111111111111111111111111111111111"
    private val token = CryptoAsset(chain = "ethereum", address = a, symbol = "SAME", name = "First project")
    private val pepe = "0x6982508145454ce325ddbe47a25d4ec3d2311933"

    @Test
    fun aTickerIsWhatAReaderIsLikelyToType() {
        assertEquals("AAPL", Watchlist.normaliseTicker("\$aapl"))
        assertEquals("MSFT", Watchlist.normaliseTicker(" msft "))
        assertEquals("BRK.B", Watchlist.normaliseTicker("BRK.B"))
        assertEquals("^GSPC", Watchlist.normaliseTicker("^GSPC"))
        assertEquals("AAPL", Watchlist.normaliseTicker("$ aapl"))
    }

    @Test
    fun anythingElseIsNoTicker() {
        assertNull(Watchlist.normaliseTicker(""))
        assertNull(Watchlist.normaliseTicker("a very long symbol name"))
        assertNull(Watchlist.normaliseTicker("AA PL"))
    }

    @Test
    fun oneSymbolIsABareCashtagAndSeveralAnOrGroup() {
        assertEquals("\$AAPL", Watchlist.postsQuery(listOf("aapl")))
        assertEquals("(\$AAPL OR \$TSLA)", Watchlist.postsQuery(listOf("AAPL", "TSLA")))
        assertEquals("", Watchlist.postsQuery(emptyList()), "an empty watchlist is no query")
    }

    @Test
    fun theFeedCapsHowManySymbolsShareOneSearch() {
        val many = (0 until Watchlist.FEED_SYMBOL_CAP + 5).map { "T$it" }
        val query = Watchlist.postsQuery(many)
        assertFalse(query.contains("\$T${Watchlist.FEED_SYMBOL_CAP}"))
        assertEquals(Watchlist.FEED_SYMBOL_CAP, query.split(" OR ").size)
    }

    @Test
    fun mixedFeedsSearchContractsLiterallyAndKeepCashtags() {
        assertEquals("(\$AAPL OR \"$a\")", Watchlist.postsQuery(listOf("AAPL", token.id)))
        assertEquals("\"$a\"", Watchlist.postsQuery(listOf(token.id)))
        assertEquals("\$BTC", Watchlist.postsQuery(listOf("BTC-USD")))
        assertEquals("\$AAPL", Watchlist.postsQuery(listOf("", "AAPL", "AAPL")))
    }

    @Test
    fun theWatchlistFeedFollowsEachTokensCashtagChoice() {
        val chosen = CryptoAsset("ethereum", pepe, "PEPE", "Pepe", includeCashtag = true)
        assertEquals("(\$AAPL OR \"$pepe\" OR \$PEPE)", Watchlist.postsQuery(listOf("AAPL", chosen.id), mapOf(chosen.id to chosen)))
        val plain = chosen.withCashtag(false)
        assertEquals("\"$pepe\"", Watchlist.postsQuery(listOf(plain.id), mapOf(plain.id to plain)))
    }

    @Test
    fun aTickerPageSearchesTheCashtagPeopleWrite() {
        assertEquals("\$SPX", Watchlist.tickerPostsQuery("^GSPC"))
        assertEquals("\$BTC", Watchlist.tickerPostsQuery("BTC-USD"))
        assertEquals("\$NVDA", Watchlist.tickerPostsQuery("nvda"))
    }

    @Test
    fun theOrderTheReaderDraggedIsKeptAndAnythingNewFiledAfterIt() {
        assertEquals(listOf("NVDA", "AAPL", "MSFT", "TSLA"), Watchlist.ordered(listOf("AAPL", "MSFT", "NVDA", "TSLA"), listOf("NVDA", "GONE", "AAPL")))
        assertEquals(listOf("AAPL", "MSFT"), Watchlist.ordered(listOf("AAPL", "MSFT"), null))
    }

    @Test
    fun storedRowsReadAsTokensByIdAndTickersAsStoredByLabelThenInTheReadersOrder() {
        val zeta = CryptoAsset("solana", "AbCdEfGh123456789AbCdEfGh123456789", "ZETA", "Zeta")
        val read = Watchlist.read(listOf("MSFT", zeta.encode(), "AAPL", token.encode()), order = null)
        assertEquals(listOf("AAPL", "MSFT", token.id, zeta.id), read.ids, "\$AAPL < \$MSFT < \$SAME < \$ZETA, tokens by their label")
        assertEquals(setOf(token.id, zeta.id), read.assets.keys)
        assertEquals("\$ZETA", read.label(zeta.id))
        assertEquals("\$AAPL", read.label("AAPL"))
        assertEquals(listOf(zeta.id, "AAPL", "MSFT", token.id), Watchlist.read(listOf("MSFT", zeta.encode(), "AAPL", token.encode()), listOf(zeta.id)).ids)
    }

    @Test
    fun aWatchedTokenNamesItsNetworkByItsContractAndTheFirstOfTwoOnOneContractLeads() {
        val onBase = CryptoAsset("base", a, "SAME", "Same on Base")
        val read = Watchlist.read(listOf("AAPL", token.encode(), onBase.encode()), listOf(onBase.id, token.id))
        assertEquals(mapOf(a to "Base"), read.contractNetworks, "tickers name none")
        assertEquals(mapOf(a to "Ethereum"), Watchlist.read(listOf(token.encode()), order = null).contractNetworks)
        assertEquals("Ethereum · 0x1111…1111", token.subtitle)
    }

    @Test
    fun anEnteredTickerOrTokenIsStoredUnderItsId() {
        assertEquals(WatchlistRow("aapl", "AAPL"), Watchlist.row("\$aapl"))
        assertEquals(WatchlistRow(token.id, token.encode()), Watchlist.row(token.encode()))
        assertNull(Watchlist.row("not a ticker"))
    }
}
