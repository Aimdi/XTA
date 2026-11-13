package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoAssetTest {
    private val a = "0x1111111111111111111111111111111111111111"
    private val b = "0x2222222222222222222222222222222222222222"
    private val asset = CryptoAsset(chain = "ethereum", address = a, symbol = "SAME", name = "First project")
    private val pepe = "0x6982508145454ce325ddbe47a25d4ec3d2311933"
    private val bonk = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"

    private fun pair(address: String = a, chain: String = "ethereum", liquidity: Double = 100.0, price: String = "0.0000000123") = mapOf(
        "chainId" to chain,
        "baseToken" to mapOf("address" to address, "symbol" to "SAME", "name" to "Project"),
        "quoteToken" to mapOf("address" to b, "symbol" to "USD"),
        "pairAddress" to "pool-address",
        "priceUsd" to price,
        "priceChange" to mapOf("h24" to 25),
        "liquidity" to mapOf("usd" to liquidity),
    )

    @Test
    fun theSameSymbolKeepsSeparateNetworkAndContractIdentities() {
        val markets = DexScreener.markets(j(mapOf("pairs" to listOf(pair(), pair(address = b), pair(chain = "base")))))
        assertEquals(3, markets.map { it.asset.id }.toSet().size)
        assertEquals(setOf("SAME"), markets.map { it.asset.symbol }.toSet())
    }

    @Test
    fun onlyIdenticalContractsMergeKeepingTheMoreLiquidPool() {
        val markets = DexScreener.markets(j(mapOf("pairs" to listOf(pair(price = "1"), pair(liquidity = 900.0, price = "2")))))
        assertEquals(1, markets.size)
        assertEquals(2.0, markets.single().price)
        assertEquals(25.0, markets.single().quote.changePercent!!, 0.000001)
    }

    @Test
    fun evmCasingCanonicalisesWhileOtherAddressesKeepTheirCase() {
        val checksum = "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD"
        assertEquals(checksum.lowercase(), CryptoAsset.canonicalAddress(checksum))
        val solana = "AbCdEfGh123456789AbCdEfGh123456789"
        assertEquals(solana, CryptoAsset.canonicalAddress(solana))
    }

    @Test
    fun theStoredFormKeepsNetworkContractNameAndSymbol() {
        val decoded = CryptoAsset.decode(asset.encode())!!
        assertEquals(asset.id, decoded.id)
        assertEquals(asset.name, decoded.name)
        assertEquals(asset.symbol, decoded.symbol)
        assertNull(CryptoAsset.decode("AAPL"))
        assertNull(CryptoAsset.decode("crypto:v1:{broken"))
        assertNull(CryptoAsset.decode("crypto:v1:{chain: base, address: $a}"), "read strictly, as XTA's jsonDecode reads it")
    }

    @Test
    fun theStoredFormIsByteForByteWhatXtaWrites() {
        val odd = CryptoAsset("base", "0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51", "PE\"PE", "Pepe \\ ü/✓ \u0001\t ", includeCashtag = true)
        // Dart's jsonEncode of the same fields, as XTA's CryptoAsset.encode writes them.
        val xta = """crypto:v1:{"chain":"base","address":"0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51","symbol":"PE\"PE","name":"Pepe \\ ü/✓ \u0001\t${" "}","cashtag":true}"""
        assertEquals(xta, odd.encode())
        assertEquals(odd.copy(name = "Pepe \\ ü/✓ \u0001"), CryptoAsset.decode(xta), "read back trimmed, as XTA reads it")
    }

    @Test
    fun malformedPayloadsCannotInventTokensOrInfinitePrices() {
        assertTrue(DexScreener.markets(J(null)).isEmpty())
        assertTrue(DexScreener.markets(J.parse("""{"pairs":[null,5,{},{"baseToken":[]}]}""")).isEmpty())
        assertNull(DexScreener.markets(j(listOf(pair(price = "NaN")))).single().price)
        assertNull(DexScreener.markets(j(listOf(pair(price = "-1")))).single().price)
        assertNull(DexScreener.markets(j(listOf(pair(price = "Infinity")))).single().price)
    }

    @Test
    fun aTokenIsNamedByItsSymbolElseItsNameElseItsAddressCutInTheMiddle() {
        val address = "0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51"
        fun of(symbol: String? = null, name: String? = null) = CryptoAsset.of("base", address, symbol, name)!!
        assertEquals("0x23a2…fe51", of().label, "never the whole address cut at the end")
        assertEquals("0x23a2…fe51", of().nameOrAddress)
        assertEquals("Lumen", of(name = "Lumen").label)
        assertEquals("\$LUM", of("LUM", "Lumen").label)
        assertEquals("\$LUM · Lumen", of("LUM", "Lumen").fullLabel)
        assertEquals("LUM", of("LUM", "Lumen").bareLabel)
        assertEquals("0x23a2…fe51", of().fullLabel)
    }

    @Test
    fun aChecksumCasedContractWithoutASymbolStillReadsCutInTheMiddleWhereXtaShowedItWhole() {
        // XTA fills a missing symbol with the address as written but keeps the contract lower-cased,
        // then compared the two exactly: `$0x6982508145454Ce325…` became the label.
        val unnamed = CryptoAsset.of("ethereum", "0x6982508145454Ce325dDbE47a25d4ec3d2311933")!!
        assertEquals(pepe, unnamed.address)
        assertEquals("0x6982…1933", unnamed.label)
        assertEquals("0x6982…1933", unnamed.bareLabel)
        assertNull(unnamed.cashtag, "an address is no cashtag")
    }

    @Test
    fun postsForATokenSearchTheContractAloneUnlessTheCashtagIsChosen() {
        val token = CryptoAsset("ethereum", pepe, "PEPE", "Pepe")
        assertEquals("\"$pepe\"", token.searchQuery)
        assertEquals("(\"$pepe\" OR \$PEPE)", token.withCashtag(true).searchQuery)
        val odd = CryptoAsset("solana", bonk, "W I F", "dogwifhat", includeCashtag = true)
        assertNull(odd.cashtag, "a symbol X cannot write as a cashtag is never added")
        assertEquals("\"$bonk\"", odd.searchQuery)
    }

    @Test
    fun theCashtagChoiceSurvivesTheStoredFormAndOldRowsReadAsOff() {
        val token = CryptoAsset("ethereum", pepe, "PEPE", "Pepe")
        assertTrue(CryptoAsset.decode(token.withCashtag(true).encode())!!.includeCashtag)
        assertFalse(token.encode().contains("cashtag"), "rows saved before stay byte-identical")
        assertFalse(CryptoAsset.decode(token.encode())!!.includeCashtag)
    }

    @Test
    fun aWatchlistIdNamesItsContract() {
        assertEquals("crypto:ethereum:$pepe", CryptoAsset("Ethereum", pepe.uppercase().replaceFirst("0X", "0x"), "PEPE", "Pepe").id)
        assertEquals(pepe, CryptoAsset.contractForId("crypto:ethereum:$pepe"))
        assertNull(CryptoAsset.contractForId("AAPL"))
        assertNull(CryptoAsset.contractForId("crypto:ethereum:short"))
    }

    @Test
    fun oneMarketPerNetworkFromEitherSideOfAPair() {
        val markets = DexScreener.marketsForAddress(J.parse(fixture("dexscreener_search.json")), pepe)
        assertEquals(listOf("ethereum", "base"), markets.map { it.asset.chain })
        val mainnet = markets.first()
        assertEquals(0.0000125, mainnet.price!!, 1e-12, "the deeper pool wins")
        assertEquals("crypto:ethereum:$pepe", mainnet.asset.id)
        assertEquals("PEPE", mainnet.asset.symbol)
        assertNull(markets.last().price, "as quote currency the pair's price is the other token's")
        assertEquals("PEPE", markets.last().asset.symbol)
    }

    @Test
    fun poolFiguresAreReadWithThePrice() {
        val pool = J.parse(fixture("dexscreener_search.json"))["pairs"][0]
        val market = DexScreener.markets(j(listOf(pool.raw))).single()
        assertEquals("0xpoolethereum", market.pairAddress)
        assertEquals(0.0000123, market.price!!, 1e-12)
        assertEquals(12.5, market.changePercent)
        assertEquals(-1.5, market.change1h)
        assertEquals(3.25, market.change6h)
        assertEquals(4_200_000.0, market.volume24h)
        assertEquals(4_900_000_000.0, market.marketCap)
        assertEquals(5_100_000_000.0, market.fdv)
        assertEquals(1000.0, market.liquidity)
        assertEquals(12.5, market.quote.changePercent!!, 1e-9)
        assertTrue(market.quote.tradesAroundTheClock)
        assertEquals(ChangeWindow.ROLLING_24H, market.quote.changeWindow)
    }

    @Test
    fun aSolanaMintDifferingOnlyInCaseIsADifferentToken() {
        val pairs = listOf(mapOf("chainId" to "solana", "baseToken" to mapOf("address" to bonk.lowercase(), "symbol" to "FAKE"), "quoteToken" to mapOf("address" to "So1")))
        assertTrue(DexScreener.marketsForAddress(j(pairs), bonk).isEmpty())
    }

    @Test
    fun aMarketsQuoteWorksItsReferenceBackFromTheMove() {
        val quote = CryptoMarket(asset, price = 125.0, changePercent = 25.0).quote
        assertEquals(100.0, quote.previousClose!!, 1e-9)
        assertEquals(asset.id, quote.symbol)
        assertEquals("USD", quote.currency)
        assertNull(CryptoMarket(asset, price = 1.0, changePercent = -100.0).quote.previousClose, "a fall of 100% has no reference")
        assertNull(CryptoMarket(asset).quote.displayPrice)
    }

    @Test
    fun aRealFigureIsFiniteAndAtLeastItsFloor() {
        assertEquals(1.5, finiteFigure(1.5))
        assertEquals(-1.5, finiteFigure(-1.5))
        assertNull(finiteFigure(-1.5, atLeast = 0.0))
        assertNull(finiteFigure(Double.NaN))
        assertNull(finiteFigure(Double.POSITIVE_INFINITY))
        assertNull(finiteFigure(null))
    }

    @Test
    fun aSearchKeepsTheContractsCaseAndDropsOnlyTheCashtagSign() {
        assertEquals("AbCdEfGh123456789AbCdEfGh123456789", DexScreener.searchUrl("AbCdEfGh123456789AbCdEfGh123456789").queryParameter("q"))
        assertEquals("PEPE", DexScreener.searchUrl(" \$PEPE ").queryParameter("q"))
        assertEquals("/token-pairs/v1/ethereum/$a", DexScreener.tokenUrl(asset).encodedPath)
        assertEquals("https://dexscreener.com/ethereum/0xpool", DexScreener.pairPageUrl(asset, "0xpool").toString())
        assertNotEquals(DexScreener.searchUrl("x"), DexScreener.tokenUrl(asset))
    }
}
