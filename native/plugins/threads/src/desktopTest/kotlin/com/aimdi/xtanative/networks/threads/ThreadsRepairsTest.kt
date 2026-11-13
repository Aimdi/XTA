package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.repairs.Repairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Threads' repairable values against XTA's published `repairs.json` at the
 * repository's root, as :networks checks the networks still there: the file
 * names exactly the Threads values this plugin knows, each of its slot's kind
 * and each the value this build ships.
 */
class ThreadsRepairsTest {
    @Test
    fun repairsJsonNamesOnlyTheThreadsValuesThisPluginKnowsAsItShipsThem() {
        val slots = ThreadsRepairs.SLOTS.associateBy { it.key }
        val published = Repairs.parse(repairsJson().readText()).filterKeys { it.startsWith("threads.") }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and ThreadsRepairs' slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindAndIsDeclaredOnce() {
        ThreadsRepairs.SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(ThreadsRepairs.SLOTS.size, ThreadsRepairs.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun aWithdrawnRepairGoesBackToTheShippedValue() {
        assertEquals(ThreadsRepairs(), ThreadsRepairs.published(emptyMap()))
        assertEquals("7000000000000001", ThreadsRepairs.published(mapOf("threads.guest_doc_id" to "7000000000000001")).guestDocId)
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/plugins/threads under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
