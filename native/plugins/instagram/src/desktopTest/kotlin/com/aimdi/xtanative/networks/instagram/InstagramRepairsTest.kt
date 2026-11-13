package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.repairs.Repairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Instagram's repairable values against XTA's published `repairs.json` at the
 * repository's root, as :networks checks the networks still there: the file
 * names exactly the Instagram values this plugin knows, each of its slot's
 * kind and each the value this build ships.
 */
class InstagramRepairsTest {
    @Test
    fun repairsJsonNamesOnlyTheInstagramValuesThisPluginKnowsAsItShipsThem() {
        val slots = InstagramRepairs.SLOTS.associateBy { it.key }
        val published = Repairs.parse(repairsJson().readText()).filterKeys { it.startsWith("instagram.") }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and InstagramRepairs' slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindAndIsDeclaredOnce() {
        InstagramRepairs.SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(InstagramRepairs.SLOTS.size, InstagramRepairs.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun theClientTakesWhatFitsAndKeepsTheRest() {
        val published = mapOf("instagram.app_id" to "111111111111111", "instagram.user_agent" to "Repaired/1.0\r\nX-Evil: 1")

        assertEquals(InstagramRepairs(appId = "111111111111111"), InstagramRepairs.published(published))
        assertEquals(InstagramRepairs(), InstagramRepairs.published(emptyMap()), "a withdrawn repair goes back to the shipped value")
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/plugins/instagram under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
