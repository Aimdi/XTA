package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchlistRowsTest {
    private val now = Instant.parse("2026-11-04T12:30:00Z")
    private val pepe = CryptoAsset.of("ethereum", "0x6982508145454Ce325dDbE47a25d4ec3d2311933", "PEPE", "Pepe")!!

    @Test
    fun aTickerOrATokenIsWatchedOnceAsXtasTableKeepsIt() {
        val rows = WatchlistRows.added(WatchlistRows.added(emptyList(), " \$aapl ", now, ZoneOffset.UTC), pepe.encode(), now, ZoneOffset.UTC)

        assertEquals(listOf(WatchlistEntryRow("aapl", "AAPL", createdAt = "2026-11-04 12:30:00"), WatchlistEntryRow(pepe.id, pepe.encode(), createdAt = "2026-11-04 12:30:00")), rows)
        assertEquals(rows, WatchlistRows.added(rows, "AAPL", now), "a ticker already watched stays as it is")
        assertEquals(rows, WatchlistRows.added(rows, "not a ticker!", now), "what is no ticker changes nothing")
        assertEquals(listOf("AAPL", pepe.encode()), WatchlistRows.symbols(rows))
    }

    @Test
    fun theRowsAreXtasBackupRowsAndReadBack() {
        val rows = listOf(WatchlistEntryRow("aapl", "AAPL", createdAt = "2026-01-01 00:00:00"), WatchlistEntryRow("tsla", "TSLA", inFeed = false))
        val text = WatchlistRows.encode(rows)

        assertEquals(rows, WatchlistRows.decode(text))
        assertEquals(listOf("id", "symbol", "in_feed", "created_at"), J.parse(text)[0].keys.toList())
        assertEquals(1L, J.parse(text)[0]["in_feed"].long)
        assertEquals(listOf(WatchlistEntryRow("x", "X")), WatchlistRows.decode("""[{"id":"x","symbol":"X"},{"id":"no symbol"},"junk"]"""))
        assertEquals(emptyList(), WatchlistRows.decode("nope"))
    }

    @Test
    fun aTickerIsRemovedByItsSymbolAndATokenByItsIdAndItsCashtagChoiceIsKept() {
        val rows = WatchlistRows.added(WatchlistRows.added(emptyList(), "AAPL", now), pepe.encode(), now)

        assertEquals(listOf(pepe.id), WatchlistRows.removed(rows, "AAPL").map { it.id })
        assertEquals(listOf("aapl"), WatchlistRows.removed(rows, pepe.id).map { it.id })
        val withCashtag = WatchlistRows.withCashtag(rows, pepe, include = true)
        assertEquals(pepe.id, withCashtag.last().id)
        assertTrue(CryptoAsset.decode(withCashtag.last().symbol)!!.includeCashtag)
    }

    @Test
    fun theStarAsksByUpperCaseAndADraggedRowMoves() {
        assertTrue(WatchlistRows.watches(listOf("AAPL"), "aapl"))
        assertFalse(WatchlistRows.watches(listOf("AAPL"), "TSLA"))
        assertEquals(listOf("b", "c", "a"), WatchlistRows.moved(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), WatchlistRows.moved(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), WatchlistRows.moved(listOf("a", "b"), 5, 0))
    }

    @Test
    fun aRouteNamesATickerOrAToken() {
        assertEquals(StocksRoute.Ticker("BRK.B"), StocksRoutes.parse(StocksRoutes.ticker("\$BRK.B")!!))
        assertEquals(StocksRoute.Ticker("^GSPC"), StocksRoutes.parse(StocksRoutes.ticker("^GSPC")!!))
        assertEquals(StocksRoute.Asset(pepe), StocksRoutes.parse(StocksRoutes.asset(pepe)))
        assertNull(StocksRoutes.ticker("not a symbol"))
        assertNull(StocksRoutes.parse("asset?token=crypto%3Av1%3Anot"))
        assertNull(StocksRoutes.parse("elsewhere"))
    }
}
