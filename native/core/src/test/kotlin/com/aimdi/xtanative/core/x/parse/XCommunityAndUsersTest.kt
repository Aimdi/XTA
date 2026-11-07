package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's community_parse_test.dart and retweeters_parser_test.dart, on its recordings. */
class XCommunityAndUsersTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/x/$name")!!.readText())

    private val flutter = "1472249315724771329"

    @Test
    fun aCommunitysHeaderReadsItsRulesAndMemberFaces() {
        val community = XCommunityParser.header(fixture("community.json"))!!
        assertEquals(flutter, community.id)
        assertEquals("Flutter", community.name)
        assertEquals(20730L, community.memberCount)
        assertEquals("Software", community.topic)
        assertTrue(!community.description.isNullOrEmpty())
        assertEquals(Instant.ofEpochMilli(1639846564596), community.createdAt)
        assertEquals(4, community.rules.size)
        assertEquals("Be kind and respectful.", community.rules.first())
        assertEquals(5, community.memberAvatarUrls.size)
        assertTrue(community.bannerUrl!!.contains("community_banner_img"), "its own banner wins over X's default one")
    }

    @Test
    fun anAnswerWithoutACommunityIsNoneNotAnError() {
        assertNull(XCommunityParser.header(J.parse("""{"data":{}}""")))
        assertNull(XCommunityParser.community(J.parse("""{"__typename":"User","rest_id":"1"}""")))
    }

    @Test
    fun aCommunitysPostsAreEachMarkedWithItAndPageOn() {
        val page = XCommunityParser.posts(fixture("community_timeline_cards.json"))
        val posts = page.threads.flatMap(XThread::posts)
        // XTA leaves out the post the community pinned; x.com shows it first, so it is kept and marked.
        assertEquals(4, posts.size, "three posts and the pinned one, unlike XTA's three")
        assertEquals(setOf("1900556614257578302"), page.pinnedIds)
        assertEquals(setOf(flutter), posts.map { it.communityId }.toSet())
        assertTrue(page.bottomCursor != null)
        val back = XCodec.decode(XCodec.encode(page.threads)).flatMap(XThread::posts)
        assertEquals(setOf(flutter), back.map { it.communityId }.toSet(), "the community survives the disk cache")
        val none = XCommunityParser.posts(J.parse("""{"data":{}}"""))
        assertTrue(none.threads.isEmpty() && none.bottomCursor == null, "a body without a timeline is an empty last page")
    }

    @Test
    fun aPageOfMembersReadsWhoAndWhereTheNextStarts() {
        val page = XCommunityParser.members(fixture("community_members.json"))
        assertEquals(3, page.users.size)
        assertEquals("tadaspetra", page.users.first().screenName)
        assertTrue(page.users.all { it.id.isNotEmpty() && it.name.isNotEmpty() })
        assertTrue(page.bottomCursor != null)
        val empty = XCommunityParser.members(J.parse("{}"))
        assertTrue(empty.users.isEmpty())
        assertNull(empty.bottomCursor, "an empty slice is an empty last page")
    }

    @Test
    fun aMemberWithoutAHandleIsLeftOut() {
        val body = J.parse(
            """{"data":{"communityResults":{"result":{"members_slice":{"items_results":[
                {"result":{"rest_id":"1","core":{"name":"No handle"}}},
                {"result":{"rest_id":"2","core":{"screen_name":"handled","name":"Handled"}}}],"slice_info":{}}}}}}""",
        )
        assertEquals(listOf("handled"), XCommunityParser.members(body).users.map { it.screenName })
    }

    @Test
    fun aPageOfCommunitySearchReadsEachCommunity() {
        val page = XCommunityParser.search(fixture("community_search.json"))
        assertEquals(listOf("Flutter", "Level Up Coding", "Flutter Developer Community"), page.communities.map { it.name })
        assertTrue(page.communities.all { it.id.isNotEmpty() })
        assertTrue(page.cursor != null)
        val empty = XCommunityParser.search(J.parse("{}"))
        assertTrue(empty.communities.isEmpty())
        assertNull(empty.cursor)
    }

    // ---- accounts in a timeline ----

    private fun retweeters(name: String) = XParser.users(fixture(name)["data"]["retweeters_timeline"]["timeline"]["instructions"])

    @Test
    fun repostersAndTheBottomCursorAreRead() {
        val page = retweeters("retweeters.json")
        assertEquals(listOf("1", "2"), page.users.map { it.id })
        val (alice, bob) = page.users
        assertEquals("alice", alice.screenName)
        assertEquals("Alice", alice.name)
        assertEquals("https://example.com/alice.jpg", alice.avatarUrl)
        assertTrue(alice.verified)
        assertEquals("bob", bob.screenName)
        assertTrue(!bob.verified)
        assertEquals("cursor-next", page.bottomCursor)
    }

    @Test
    fun peopleInAModuleAreReadUnderEitherName() {
        val page = retweeters("retweeters_module.json")
        assertEquals(listOf("dana", "erin"), page.users.map { it.screenName })
        assertEquals(listOf("9", "10"), page.users.map { it.id })
        assertTrue(page.users.last().verified, "under userResults too")
        assertEquals("cursor-module-next", page.bottomCursor)
    }

    @Test
    fun missingOrReshapedAnswersAreAnEmptyPage() {
        listOf("null", "{}", "\"nope\"", """{"data":null}""").forEach { body ->
            val page = XParser.users(J.parse(body)["data"]["retweeters_timeline"]["timeline"]["instructions"])
            assertTrue(page.users.isEmpty(), body)
            assertNull(page.bottomCursor)
        }
    }

    @Test
    fun anUnreadableAccountIsSkippedAndTheRestOfThePageSurvives() {
        val instructions = J.parse(
            """[{"type":"TimelineAddEntries","entries":[
                {"entryId":"user-bad","content":{"itemContent":null}},
                "not-a-map",
                {"entryId":"user-3","content":{"itemContent":{"user_results":{"result":{"rest_id":"3","core":{"screen_name":"cara","name":"Cara"}}}}}}]}]""",
        )
        assertEquals(listOf("cara"), XParser.users(instructions).users.map { it.screenName })
    }
}
