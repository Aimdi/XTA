package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.repairs.Repairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Stocks' repairable values against XTA's published `repairs.json` at the
 * repository's root, as :networks checks the networks still there: the file
 * names exactly the Stocks values this plugin knows, each of its slot's kind
 * and each the value this build ships.
 */
class StocksRepairsTest {
    @Test
    fun repairsJsonNamesOnlyTheStocksValuesThisPluginKnowsAsItShipsThem() {
        val slots = TickerClient.REPAIR_SLOTS.associateBy { it.key }
        val published = Repairs.parse(repairsJson().readText()).filterKeys { it.startsWith("stocks.") }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and TickerClient's slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindAndIsDeclaredOnce() {
        TickerClient.REPAIR_SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(TickerClient.REPAIR_SLOTS.size, TickerClient.REPAIR_SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun aWithdrawnRepairGoesBackToTheShippedValue() {
        assertEquals("Repaired/2.0", TickerClient.USER_AGENT_SLOT.valueIn(mapOf("stocks.user_agent" to "Repaired/2.0")))
        assertEquals(TickerClient.DEFAULT_USER_AGENT, TickerClient.USER_AGENT_SLOT.valueIn(emptyMap()))
    }

    @Test
    fun aRepairPublishedMeanwhileReachesTheNextRequest(): Unit = runBlocking {
        var published = emptyMap<String, String>()
        val http = FakeHttp { answer(fixture("yahoo_trending.json")) }
        val client = TickerClient(http, coinSources = emptyList(), health = PriceSourceHealth(), userAgent = { TickerClient.USER_AGENT_SLOT.valueIn(published) })

        client.fetchTrending()
        published = mapOf("stocks.user_agent" to "Mozilla/5.0 (Linux) XTA")
        client.fetchTrending()

        assertEquals(listOf(TickerClient.DEFAULT_USER_AGENT, "Mozilla/5.0 (Linux) XTA"), http.headers.map { it["User-Agent"] })
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/plugins/stocks under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
