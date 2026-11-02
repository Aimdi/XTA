package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * XTA's timeline parser rules: show_more_cursor_test.dart, tombstone_test.dart,
 * subscriber_preview_test.dart and the cursor half of parser_resilience_test.dart.
 */
class XTimelineParserTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/x/$name")!!.readText())

    private fun result(id: String, text: String = "reply $id") =
        """{"rest_id":"$id","core":{"user_results":{"result":{"rest_id":"1","core":{"screen_name":"someone","name":"Someone"}}}},"legacy":{"id_str":"$id","full_text":"$text"}}"""

    private fun tweet(id: String) = """{"entryId":"tweet-$id","content":{"itemContent":{"tweet_results":{"result":${result(id)}}}}}"""

    private fun item(thread: String, id: String, results: String) =
        """{"entryId":"conversationthread-$thread-tweet-$id","item":{"itemContent":{"itemType":"TimelineTweet","tweet_results":$results}}}"""

    private fun thread(id: String, vararg items: String) = """{"entryId":"conversationthread-$id","content":{"items":[${items.joinToString(",")}]}}"""

    private fun instructions(vararg instructions: String) =
        J.parse("""{"data":{"threaded_conversation_with_injections_v2":{"instructions":[${instructions.joinToString(",")}]}}}""")

    private fun added(vararg entries: String) = """{"type":"TimelineAddEntries","entries":[${entries.joinToString(",")}]}"""

    private fun conversation(vararg entries: String): XTimelinePage = XParser.conversation(instructions(added(*entries)))

    private fun showMore(entryId: String, content: String) = conversation(tweet("1"), """{"entryId":"$entryId","content":$content}""").showMoreCursor

    // ---- the cursor behind "Show additional replies" ----

    @Test
    fun theShowMoreCursorIsReadInEachShapeXHasSentIt() {
        assertEquals("SHOWMORE-A", showMore("cursor-showMore-8", """{"value":"SHOWMORE-A"}"""))
        assertEquals("SHOWMORE-B", showMore("cursor-showmorethreads-9", """{"operation":{"cursor":{"value":"SHOWMORE-B","cursorType":"ShowMoreThreads"}}}"""))
        assertEquals("SHOWMORE-C", showMore("cursor-showmorethreadsprompt-10", """{"itemContent":{"value":"SHOWMORE-C","cursorType":"ShowMoreThreadsPrompt"}}"""))
        assertEquals("SHOWMORE-D", showMore("CURSOR-SHOWMORE-11", """{"value":"SHOWMORE-D"}"""), "whatever case X spells it in")
    }

    @Test
    fun theBottomCursorIsPagingNotShowMore() {
        val page = conversation(tweet("1"), """{"entryId":"cursor-bottom-12","content":{"value":"BOTTOM"}}""")
        assertNull(page.showMoreCursor)
        assertEquals("BOTTOM", page.bottomCursor)
        assertNull(conversation(tweet("1")).showMoreCursor, "a conversation with nothing withheld offers none")
    }

    @Test
    fun aShowMoreCursorOfAShapeNotKnownIsNoCursorAndNoError() {
        listOf("null", "\"not a map\"", "{}", """{"operation":{}}""", """{"itemContent":null}""", """{"value":12345}""").forEach { content ->
            assertNull(showMore("cursor-showmore-13", content), content)
        }
        assertTrue(conversation("null", "{}").threads.isEmpty())
    }

    @Test
    fun aShowMoreCursorNestedInAConversationModuleIsReadAndMakesNoThread() {
        val nested = """{"entryId":"conversationthread-9-cursor-showmore-9","item":{"itemContent":{"itemType":"TimelineTimelineCursor","cursorType":"ShowMoreThreads","value":"SHOWMORE-NESTED"}}}"""
        val page = conversation(tweet("1"), thread("9", nested))
        assertEquals("SHOWMORE-NESTED", page.showMoreCursor)
        assertEquals(listOf("1"), page.threads.map { it.id }, "a module with only a cursor is no reply thread")
        val both = conversation(thread("9", nested.replace("SHOWMORE-NESTED", "NESTED")), """{"entryId":"cursor-showMore-8","content":{"value":"TOP"}}""")
        assertEquals("TOP", both.showMoreCursor, "the one at the top of the page wins")
    }

    // ---- later pages: replies X adds to a module ----

    private fun addToModule(module: String?, vararg items: String) =
        """{"type":"TimelineAddToModule",${module?.let { "\"moduleEntryId\":\"$it\"," }.orEmpty()}"moduleItems":[${items.joinToString(",")}]}"""

    @Test
    fun repliesAddedToAModuleOnALaterPageAreItsThread() {
        val page = XParser.conversation(
            instructions(
                addToModule("conversationthread-9", item("9", "2", """{"result":${result("2")}}"""), item("9", "3", """{"result":${result("3")}}""")),
                addToModule(null, item("10", "4", """{"result":${result("4")}}""")),
            ),
        )
        assertEquals(listOf(listOf("2", "3"), listOf("4")), page.threads.map { thread -> thread.posts.map { it.id } })
        assertEquals("conversationthread-10", page.threads.last().id, "a module X does not name is named by its items")
    }

    @Test
    fun aModuleItemWithoutItsTypeButWithAResultIsKept() {
        val untyped = """{"entryId":"conversationthread-9-tweet-2","item":{"itemContent":{"tweet_results":{"result":${result("2")}}}}}"""
        assertEquals("2", XParser.conversation(instructions(addToModule("conversationthread-9", untyped))).threads.single().posts.single().id)
    }

    @Test
    fun cursorsNestedInModuleItemsAreRead() {
        val showMore = """{"entryId":"conversationthread-9-cursor-showmore-9","item":{"itemContent":{"itemType":"TimelineTimelineCursor","cursorType":"ShowMoreThreads","value":"SHOWMORE-MOD"}}}"""
        val bottom = """{"entryId":"cursor-bottom-0","item":{"itemContent":{"value":"BOTTOM-MOD"}}}"""
        val page = XParser.conversation(instructions(addToModule("conversationthread-9", showMore, bottom)))
        assertEquals("SHOWMORE-MOD", page.showMoreCursor)
        assertEquals("BOTTOM-MOD", page.bottomCursor)
        assertTrue(page.threads.isEmpty())
    }

    // ---- posts X withholds ----

    @Test
    fun aDeletedReplyKeepsItsPlaceWithXsOwnWords() {
        val deleted = """{"result":{"__typename":"TweetTombstone","tombstone":{"text":{"text":"This Post was deleted by the Post author. Learn more"}}}}"""
        val page = conversation(thread("100", item("100", "101", """{"result":${result("101")}}"""), item("100", "102", deleted)))
        val thread = page.threads.single()
        assertEquals(listOf("101"), thread.posts.map { it.id })
        val gap = thread.withheld.single()
        assertEquals("102", gap.id, "its id, so it sorts into its place")
        assertEquals("This Post was deleted by the Post author.", gap.reason, "X's own wording, without its Learn more")
    }

    @Test
    fun aWithheldReplyWithoutAnExplanationHasNone() {
        val gap = conversation(thread("300", item("300", "301", """{"result":{"__typename":"TweetUnavailable"}}"""))).threads.single().withheld.single()
        assertNull(gap.reason)
        assertEquals(false, gap.subscribersOnly)
    }

    @Test
    fun anItemCarryingNoResultAtAllIsSkipped() {
        val thread = conversation(thread("400", item("400", "401", "{}"), item("400", "402", """{"result":${result("402")}}"""))).threads.single()
        assertEquals(listOf("402"), thread.posts.map { it.id })
        assertTrue(thread.withheld.isEmpty(), "an absent result is a shape not known, not a withheld post")
    }

    @Test
    fun aProfilesConversationKeepsItsWithheldPostsToo() {
        val gone = """{"result":{"tombstone":{"text":{"text":"gone"}}}}"""
        val module = """{"entryId":"profile-conversation-500","content":{"items":[${item("500", "501", gone)}]}}"""
        val profile = added(module)
        val page = XParser.timeline(J.parse("""{"data":{"user":{"result":{"timeline":{"timeline":{"instructions":[$profile]}}}}}}"""))
        assertEquals("gone", page.threads.single().withheld.single().reason)
    }

    @Test
    fun aConversationKeepsTheFocalPostXWithholdsAFeedSkipsIt() {
        val withheld = """{"entryId":"tweet-7","content":{"itemContent":{"tweet_results":{"result":{"__typename":"TweetUnavailable","reason":"Suspended"}}}}}"""
        assertEquals("7", conversation(withheld).threads.single().withheld.single().id)
        assertTrue(XParser.timeline(instructions(added(withheld))).threads.isEmpty())
        assertTrue(conversation("""{"entryId":"tweet-8","content":{"itemContent":{"tweet_results":{}}}}""").threads.isEmpty(), "no result is no post at all")
    }

    // ---- subscriber previews (QuaX #188, recordings of x.com/Osemka8) ----

    private val previewId = "2105589900078641579"

    @Test
    fun aSubscriberOnlyPostReadsAsThePreviewXSends() {
        val page = XParser.conversation(fixture("tweet_detail_subscriber_preview.json"))
        val focal = page.threads.first { it.id == previewId }.posts.single()
        assertTrue(focal.subscriberPreview, "a preview, not a deleted post")
        assertEquals("I know I've written about this for you guys, but c…", focal.text)
        assertEquals("Osemka8", focal.author.screenName, "the author comes only in the newer shape")
        assertEquals("Osemka", focal.author.name)
        assertEquals(Instant.parse("2026-10-01T09:25:26Z"), focal.createdAt)
        assertEquals(8L, focal.likeCount)
        assertEquals(1L, focal.replyCount)
        assertEquals(250L, focal.viewCount)
        assertEquals("I know I've written about this for you guys, but c…", XText.plain(focal))
        assertTrue(page.bottomCursor != null)
    }

    @Test
    fun aReplyHiddenAsExclusiveSaysSoRatherThanDeleted() {
        val hidden = XParser.conversation(fixture("tweet_detail_subscriber_preview.json")).threads.flatMap { it.withheld }.single()
        assertEquals("2105590666927513924", hidden.id)
        assertTrue(hidden.subscribersOnly)
    }

    @Test
    fun thePreviewSurvivesTheDiskCache() {
        val threads = XParser.conversation(fixture("tweet_detail_subscriber_preview.json")).threads
        val back = XCodec.decode(XCodec.encode(threads))
        val focal = back.first { it.id == previewId }.posts.single()
        assertTrue(focal.subscriberPreview)
        assertEquals(threads.flatMap { it.withheld }, back.flatMap { it.withheld })
    }

    @Test
    fun thePreviewSurvivesLosingOrReshapingAnySingleField() {
        val result = fixture("tweet_detail_subscriber_preview.json")["data"]["threaded_conversation_with_injections_v2"]["instructions"][1]["entries"][0]["content"]["itemContent"]["tweet_results"]["result"]
        val preview = result["tweet"].raw as JsonObject
        for (key in preview.keys) {
            for (value in listOf(null, JsonPrimitive("reshaped"))) {
                val changed = JsonObject(if (value == null) preview - key else preview + (key to value))
                XParser.post(J(JsonObject((result.raw as JsonObject) + ("tweet" to changed))))
            }
        }
    }

    @Test
    fun aProfileMixingPreviewsIntoItsPostsKeepsEveryOne() {
        val threads = XParser.timeline(fixture("user_tweets_subscriber_previews.json")).threads
        assertEquals(3, threads.size)
        assertEquals("2105651449673761205", threads[0].id)
        assertTrue(threads[1].id.endsWith("2105664464454942722"))
        assertEquals(previewId, threads[2].id)
        assertEquals(3, threads.flatMap { it.posts }.count { it.subscriberPreview })
        assertTrue(threads[1].posts.size == 2 && threads[1].posts.all { it.subscriberPreview && it.author.screenName == "Osemka8" }, "the thread X names profile-originals-conversation")
    }

    // ---- paging cursors in every shape (parser_resilience_test.dart) ----

    @Test
    fun aCursorIsReadInEachShapeAndNeverThrows() {
        assertEquals("next-page", conversation("""{"entryId":"cursor-bottom-0","content":{"value":"next-page","cursorType":"Bottom"}}""").bottomCursor)
        assertEquals("sq-next", conversation("""{"entryId":"sq-C-1","content":{"operation":{"cursor":{"value":"sq-next","cursorType":"Bottom"}}}}""").bottomCursor)
        val replaced = XParser.timeline(
            instructions(
                """{"type":"TimelineReplaceEntry","replaceEntry":{"entryIdToReplace":"cursor-bottom-0","entry":{"content":{"operation":{"cursor":{"value":"replaced","cursorType":"Bottom"}}}}}}""",
                """{"type":"TimelineReplaceEntry","notACursor":true}""",
            ),
        )
        assertEquals("replaced", replaced.bottomCursor)
        val junk = conversation("""{"entryId":null,"content":null}""", """{"content":{"value":"orphan"}}""", "\"not-a-map\"", """{"entryId":"cursor-bottom-0","content":{"operation":null}}""")
        assertNull(junk.bottomCursor)
        assertNull(conversation("""{"entryId":"cursor-bottom-0","content":{"value":42}}""").bottomCursor, "a cursor that is no string is no cursor")
    }

    @Test
    fun aMediaGridIsAPostPerTileWhateverShapeItsItemsTake() {
        val tile = { id: String -> """{"entryId":"profile-grid-0-tweet-$id","item":{"itemContent":{"tweet_results":{"result":${result(id)}}}}}""" }
        val older = { id: String -> """{"entryId":"profile-grid-0-tweet-$id","item":{"content":{"tweetResult":{"result":${result(id)}}}}}""" }
        val page = XParser.timeline(
            instructions(
                added("""{"entryId":"profile-grid-0","content":{"entryType":"TimelineTimelineModule","items":[${tile("1")},${tile("2")}]}}"""),
                addToModule("profile-grid-0", older("3")),
            ),
        )
        assertEquals(listOf("1", "2", "3"), page.threads.map { it.id })
        assertTrue(page.threads.all { it.posts.size == 1 })
    }

    @Test
    fun aGoodPostSurvivesASiblingThatLostItsContent() {
        val entries = fixture("user_tweets_add_entries.json")["entries"].list
        val mangled = listOf(J.parse("""{"entryId":"tweet-1","content":null}"""), J.parse("""{"entryId":null}""")) + entries
        val page = XParser.entries(mangled)
        assertEquals(XParser.entries(entries).threads.map(XThread::id), page.threads.map(XThread::id))
    }
}
