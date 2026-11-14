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

        val waiting = ThreadsRepairs.WAITING.map { it.key }.toSet()
        assertEquals(slots.keys - waiting, published.keys - waiting)
        published.forEach { (key, value) ->
            val slot = slots.getValue(key)
            assertTrue(slot.kind.fits(value), key)
            // A read that waits for its doc id ships none: repairs.json may name one, which switches the read on.
            if (key !in waiting) assertEquals(slot.shipped, value, "$key: change repairs.json and ThreadsRepairs' slot together")
        }
    }

    @Test
    fun everyShippedValueFitsItsKindOrIsAReadWaitingForItsIdAndIsDeclaredOnce() {
        (ThreadsRepairs.SLOTS - ThreadsRepairs.WAITING.toSet()).forEach { assertTrue(it.kind.fits(it.shipped), it.key) }
        assertEquals(
            setOf(
                "threads.inline_replies_doc_id", "threads.about_profile_doc_id", "threads.trending_topics_doc_id",
                "threads.inline_replies_expansion", "threads.search_users_doc_id", "threads.recommended_users_doc_id",
            ),
            ThreadsRepairs.WAITING.map { it.key }.toSet(),
        )
        assertEquals(ThreadsRepairs.SLOTS.size, ThreadsRepairs.SLOTS.map { it.key }.toSet().size)
    }

    @Test
    fun aReadWaitingForItsIdIsSwitchedOnByAPublishedIdOfItsKindAndByNothingElse() {
        assertEquals("", ThreadsRepairs().inlineRepliesDocId)
        assertEquals("", ThreadsRepairs.published(mapOf("threads.inline_replies_doc_id" to "not-an-id")).inlineRepliesDocId)
        val on = ThreadsRepairs.published(mapOf("threads.inline_replies_doc_id" to "7000000000000001", "threads.inline_replies_variable" to "mediaID"))
        assertEquals("7000000000000001", on.inlineRepliesDocId)
        assertEquals("mediaID", on.inlineRepliesVariable)
        assertEquals("userID", on.aboutProfileVariable)
    }

    @Test
    fun aGuestsPeopleReadsShipOffAndAPublishedIdSwitchesEachOn() {
        assertEquals("", ThreadsRepairs().searchUsersDocId)
        assertEquals("", ThreadsRepairs().recommendedUsersDocId)
        assertEquals("query", ThreadsRepairs().searchUsersVariable)
        assertEquals("", ThreadsRepairs().inlineRepliesExpansion)
        val on = ThreadsRepairs.published(
            mapOf(
                "threads.search_users_doc_id" to "7000000000000004",
                "threads.search_users_variable" to "search_query",
                "threads.recommended_users_doc_id" to "7000000000000005",
                "threads.inline_replies_expansion" to "CHILDREN",
            ),
        )
        assertEquals("7000000000000004", on.searchUsersDocId)
        assertEquals("search_query", on.searchUsersVariable)
        assertEquals("7000000000000005", on.recommendedUsersDocId)
        assertEquals("CHILDREN", on.inlineRepliesExpansion)
        assertEquals("", ThreadsRepairs.published(mapOf("threads.search_users_doc_id" to "an id")).searchUsersDocId)
    }

    @Test
    fun theRefetchQueriesShipWithTheirIdsAndAPublishedZeroWithdrawsOne() {
        val shipped = ThreadsRepairs()
        assertEquals("28965099446515427", shipped.refetchDocId(ThreadsRefetch.REPLIES))
        assertEquals("29180318631617452", shipped.refetchDocId(ThreadsRefetch.SEARCH))
        assertEquals("29222947497321867", shipped.refetchDocId(ThreadsRefetch.PROFILE_THREADS))
        val published = ThreadsRepairs.published(mapOf("threads.search_refetch_doc_id" to "0", "threads.replies_refetch_doc_id" to "7000000000000006"))
        assertEquals("", published.refetchDocId(ThreadsRefetch.SEARCH), "repairs.json can name no empty value: zero is off")
        assertEquals("7000000000000006", published.refetchDocId(ThreadsRefetch.REPLIES))
        assertEquals(shipped.profileRefetchDocId, published.refetchDocId(ThreadsRefetch.PROFILE_THREADS))
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
