package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/** XTA's import_handles_test, list import, subscription_health_test, subscription_profiles_test and subscription_pack_test, for the native port. */
class FollowsTest {
    @Test
    fun handlesAreReadFromAtNamesBareNamesAndProfileLinks() {
        assertEquals(listOf("nasa", "SpaceX", "elonmusk", "jack"), ImportHandles.parse("@nasa\nSpaceX https://x.com/elonmusk twitter.com/jack"))
        assertEquals(listOf("NASA"), ImportHandles.parse("@NASA nasa !!! too-long-handle-name"))
        assertTrue(ImportHandles.parse("").isEmpty())
        assertTrue(ImportHandles.parse("https://example.com/nasa").isEmpty())
        assertNull(ImportHandles.fromToken("https://x.com/i/status/1"))
        assertEquals("grok", ImportHandles.fromToken("https://x.com/grok/"))
    }

    @Test
    fun aListIsNamedByItsIdOrItsLink() {
        assertEquals("123", ListImport.listId(" 123 "))
        assertEquals("1234567890123456789", ListImport.listId("https://x.com/i/lists/1234567890123456789"))
        assertEquals("42", ListImport.listId("https://twitter.com/i/lists/42?s=20"))
        assertNull(ListImport.listId("https://x.com/nasa"))
        assertNull(ListImport.listId("x.com/i/lists/42"), "XTA reads a link without its scheme as a path")
        assertNull(ListImport.listId("not a list"))
    }

    private class Lists(val name: String?, val pages: Map<String?, MemberPage>) : ListReads {
        val asked = ArrayList<String?>()

        override suspend fun listName(listId: String) = name

        override suspend fun listMembers(listId: String, cursor: String?): MemberPage {
            asked += cursor
            return pages[cursor] ?: MemberPage(emptyList(), null)
        }
    }

    private fun user(id: String) = XUser(id, "handle$id", "Name $id")

    @Test
    fun aListIsReadPageByPageEachMemberOnceUntilItRunsOut(): Unit = runBlocking {
        val lists = Lists(
            "Space",
            mapOf(
                null to MemberPage(listOf(user("1"), user("2")), "c1"),
                "c1" to MemberPage(listOf(user("2"), user("3"), XUser("", "", "")), "c2"),
                "c2" to MemberPage(listOf(user("3")), "c3"),
            ),
        )
        val progress = ArrayList<Int>()
        val list = ListImport.read("42", lists, progress::add)
        assertEquals(ImportedList("42", "Space", listOf(user("1"), user("2"), user("3"))), list)
        assertEquals(listOf(null, "c1", "c2"), lists.asked, "a page that brought nobody new ends it")
        assertEquals(listOf(2, 3), progress)

        for (end in listOf(null, "", "0")) {
            val once = Lists("Space", mapOf(null to MemberPage(listOf(user("1")), end)))
            ListImport.read("42", once)
            assertEquals(listOf<String?>(null), once.asked)
        }
        val repeating = Lists("Space", mapOf(null to MemberPage(listOf(user("1")), "same"), "same" to MemberPage(listOf(user("2")), "same")))
        assertEquals(2, ListImport.read("42", repeating).members.size)
        assertEquals(listOf(null, "same"), repeating.asked)
        assertFailsWith<ListNotFoundException> { ListImport.read("42", Lists(null, emptyMap())) }
    }

    @Test
    fun aHandleThatCannotBeLookedUpIsNeverTakenForAMissingAccount() = runBlocking {
        var askedById = false
        val offline = FollowCheck.check<String>(byName = { throw IOException("offline") }, byId = { askedById = true; AccountLookup.Found("valid") })
        assertEquals(FollowHealth.UNREACHABLE, offline.health)
        assertFalse(askedById)
        for (error in listOf(IOException("offline"), IllegalStateException("shape"))) {
            assertEquals(FollowHealth.UNREACHABLE, FollowCheck.check<String>({ AccountLookup.Missing }, { throw error }).health)
        }
        assertFalse(FollowHealth.UNREACHABLE.removable)
    }

    @Test
    fun missingSuspendedAndRenamedAccountsStayApart() = runBlocking {
        assertEquals(FollowHealth.MISSING, FollowCheck.check<String>({ AccountLookup.Missing }, { AccountLookup.Missing }).health)
        assertEquals(FollowHealth.SUSPENDED, FollowCheck.check<String>({ AccountLookup.Missing }, { AccountLookup.Suspended }).health)
        assertEquals(HealthCheck(FollowHealth.RENAMED, "new-name"), FollowCheck.check({ AccountLookup.Missing }, { AccountLookup.Found("new-name") }))
        assertEquals(HealthCheck(FollowHealth.EXISTS, "same"), FollowCheck.check({ AccountLookup.Found("same") }, { error("not asked") }))
        assertTrue(FollowHealth.MISSING.removable && FollowHealth.SUSPENDED.removable)
    }

    @Test
    fun rateLimitsAndHungLookupsAreNotRemovals() = runBlocking {
        assertEquals(FollowHealth.RATE_LIMITED, FollowCheck.check<String>({ AccountLookup.Missing }, { throw XRateLimitedException() }).health)
        val hung = FollowCheck.check<String>({ CompletableDeferred<AccountLookup<String>>().await() }, { AccountLookup.Found("unused") }, timeoutMillis = 10)
        assertEquals(FollowHealth.UNREACHABLE, hung.health)
    }

    private fun post(id: String, by: String, reposts: XPost? = null, quotes: XPost? = null) =
        XPost(id, XUser(by, by, by), null, "", reposted = reposts, quoted = quotes)

    @Test
    fun theAccountsBehindPostsAreTheirAuthorsAndWhoTheyRepostAndQuote() {
        val threads = listOf(
            XThread("1", listOf(post("p1", "a", reposts = post("p2", "b", quotes = post("p3", "c"))))),
            XThread("2", listOf(post("p4", "d"))),
        )
        assertEquals(listOf("a", "b", "c", "d"), ProfileRefresh.postAuthors(threads).map { it.id })
    }

    private fun picture(id: String, size: String) = "https://pbs.twimg.com/profile_images/1/${id}_$size.jpg"

    private val stored = FollowedAccount("1", "handle_1", "Name 1", picture("1", "normal"), verified = false, inFeed = false, followedAt = 3)

    @Test
    fun aNewHandleNameOrPictureIsPickedUpAndTheFeedSettingsKept() {
        for (sent in listOf(XUser("1", "renamed", "Name 1", picture("1", "normal")), XUser("1", "handle_1", "New name", picture("1", "normal")), XUser("1", "handle_1", "Name 1", "https://pbs.twimg.com/profile_images/2/new_normal.jpg"))) {
            val fresh = ProfileRefresh.refreshed(listOf(stored), listOf(sent)).getValue("1")
            assertEquals(Triple(sent.screenName, sent.name, sent.avatarUrl), Triple(fresh.screenName, fresh.name, fresh.avatarUrl))
            assertEquals(false to 3L, fresh.inFeed to fresh.followedAt)
        }
        for (size in listOf("400x400", "bigger", "mini", "reasonably_small")) {
            assertTrue(ProfileRefresh.refreshed(listOf(stored), listOf(XUser("1", "handle_1", "Name 1", picture("1", size)))).isEmpty())
        }
        assertTrue(ProfileRefresh.refreshed(listOf(stored), listOf(XUser("2", "x", "Someone else"))).isEmpty())
        assertTrue(ProfileRefresh.refreshed(listOf(stored), listOf(XUser("1", "handle_1", "  ", ""))).isEmpty(), "what X left out stays")
    }

    @Test
    fun aChangeOfCaseIsTheSameHandle() {
        assertFalse(ProfileRefresh.renamed(stored, stored.copy(screenName = "Handle_1")))
        assertTrue(ProfileRefresh.renamed(stored, stored.copy(screenName = "renamed")))
    }

    @Test
    fun aPackReadsBackAsWrittenAndAnotherFormatOrVersionIsRefused() {
        val pack = GroupPack("Tech", listOf(PackMember("user", "783214", "X"), PackMember("search", "dart lang")))
        assertEquals(pack, GroupPack.decode(GroupPack.encode(pack)))
        assertNull(GroupPack.decode("""{"format":"other","v":1,"name":"x","members":[]}"""))
        assertNull(GroupPack.decode("""{"format":"xta-pack","v":2,"name":"x","members":[]}"""))
        assertNull(GroupPack.decode("not json"))
        assertEquals(GroupPack("x", listOf(PackMember("user", "1"))), GroupPack.decode("""{"format":"xta-pack","v":1,"name":"x","members":[{"type":"user","id":"1"},"junk"]}"""))
    }
}
