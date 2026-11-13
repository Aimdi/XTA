package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.repairs.Repairs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Reddit's repairable values against XTA's published `repairs.json` at the
 * repository's root, which both apps read, as :networks checks the networks
 * still there: the file's Reddit entries are exactly the slots the plugin
 * knows, each of its slot's kind and each the value this build ships.
 */
class RedditRepairsTest {
    @Test
    fun repairsJsonNamesExactlyRedditsSlotsEachTheValueThisBuildShips() {
        val published = Repairs.parse(repairsJson().readText()).filterKeys { it.startsWith("reddit.") }
        val slots = RedditClient.REPAIR_SLOTS.associateBy { it.key }

        assertEquals(slots.keys, published.keys)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            assertEquals(slot.shipped, value, "$key: change repairs.json and RedditClient's slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindAndEachKeyIsDeclaredOnce() {
        RedditClient.REPAIR_SLOTS.forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(RedditClient.REPAIR_SLOTS.size, RedditClient.REPAIR_SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun theAgentInForceIsTheRepairedOneWhenItFitsElseTheShippedOne() {
        val slot = RedditClient.WEB_USER_AGENT_SLOT
        assertEquals(RedditClient.WEB_USER_AGENT, slot.valueIn(emptyMap()))
        assertEquals("Repaired/2.0", slot.valueIn(mapOf("reddit.web_user_agent" to "Repaired/2.0")))
        assertEquals(RedditClient.WEB_USER_AGENT, slot.valueIn(mapOf("reddit.web_user_agent" to "Two\r\nLines")))
    }

    @Test
    fun theReadersAreMadeAnewOnlyWhenTheRepairedAgentChanges() {
        var published = emptyMap<String, String>()
        val readers = RedditReaders(FakeReddit { page("") }) { published }
        val first = readers.current()

        published = mapOf("tiktok.aid" to "2000")
        assertSame(first, readers.current())
        assertEquals(RedditClient.WEB_USER_AGENT, first.agent)

        published = mapOf("reddit.web_user_agent" to "Repaired/2.0")
        val repaired = readers.current()
        assertNotSame(first, repaired)
        assertEquals("Repaired/2.0", repaired.agent)
        assertSame(repaired, readers.current())
    }

    /** The repository's `repairs.json`, found from wherever the tests run (native/plugins/reddit under Gradle). */
    private fun repairsJson(): File {
        val found = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "repairs.json") }
            .firstOrNull { it.isFile && File(it.parentFile, "native").isDirectory }
        return assertNotNull(found, "repairs.json beside native/")
    }
}
