package com.aimdi.xtanative.networks

import com.aimdi.xtanative.core.repairs.Repairs
import com.aimdi.xtanative.networks.stocks.TickerClient
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every network's repairable values against XTA's published `repairs.json`
 * at the repository's root, which both apps read: the file names exactly the
 * values the networks here know, each of its slot's kind and each the value
 * this build ships, so a repair is made in the file and in the code together.
 * The values of a network that became a plugin (`native/plugins/<id>`) are
 * checked the same way by that plugin's own tests.
 */
class NetworkRepairsTest {
    @Test
    fun repairsJsonNamesOnlyKnownValuesThatFitEachTheOneThisBuildShips() {
        val slots = NetworkRepairs.SLOTS.associateBy { it.key }
        val here = slots.keys.map(::networkOf).toSet()
        val published = Repairs.parse(repairsJson().readText()).filterKeys { networkOf(it) in here }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and the network's slot together")
        }
    }

    @Test
    fun everyOtherPublishedValueBelongsToAPluginThatChecksIt() {
        val here = NetworkRepairs.SLOTS.map { networkOf(it.key) }.toSet()
        val plugins = File(repairsJson().parentFile, "native/plugins").listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        Repairs.parse(repairsJson().readText()).keys.filterNot { networkOf(it) in here }.forEach { key ->
            assertTrue(networkOf(key) in plugins, "$key: no network here or plugin in native/plugins has it")
        }
    }

    @Test
    fun everyShippedValueFitsItsOwnKind() {
        NetworkRepairs.SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
    }

    @Test
    fun everyKeyIsDeclaredOnce() {
        assertEquals(NetworkRepairs.SLOTS.size, NetworkRepairs.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun aWithdrawnRepairGoesBackToTheShippedValue() {
        assertEquals("Repaired/2.0", TickerClient.USER_AGENT_SLOT.valueIn(mapOf("stocks.user_agent" to "Repaired/2.0")))
    }

    /** The network a key repairs: `pixiv` of `pixiv.app_version`. */
    private fun networkOf(key: String): String = key.substringBefore('.')

    /** The repository's `repairs.json`, found from wherever the tests run (native/networks under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
