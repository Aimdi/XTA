package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Recorded X answers from XTA's test/fixtures (see its README), read the way XTA reads them. */
class XParserTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/x/$name")!!.readText())

    @Test
    fun aProfilesPostsPageGivesItsPostsQuotesViewsAndCursors() {
        val page = XParser.timeline(fixture("user_originals_page.json"))

        assertEquals(listOf("2095951992489152745", "2095934584533680451"), page.threads.map { it.id })
        val (first, second) = page.threads.map { it.posts.single() }
        assertEquals("quax_tests_2", first.author.screenName)
        assertEquals("Quoting tweet with limited replies", first.text)
        assertEquals(18L, first.viewCount)
        assertEquals("Tweet with limited replies @quax_tests_2", first.quoted?.text, "a quote with limited replies is unwrapped")
        assertNull(second.quoted, "a quote of a deleted post shows no quote")
        assertTrue(page.bottomCursor!!.startsWith("DAAHCgABHT0JWG"))
        assertNotNull(page.topCursor)
    }

    @Test
    fun aCurrentUserResultWithoutLegacyStillReads() {
        val (user, pinned) = XParser.profile(fixture("user_modern.json"))!!
        assertEquals("2095909295913103360", user.id)
        assertEquals("quax_tests", user.screenName)
        assertEquals("QuaX Tests", user.name)
        assertEquals("https://pbs.twimg.com/profile_images/2095923817080295424/YjqaAMdO_normal.jpg", user.avatarUrl)
        assertEquals("https://pbs.twimg.com/profile_images/2095923817080295424/YjqaAMdO_200x200.jpg", user.largeAvatarUrl)
        assertEquals(2026, user.createdAt!!.atZone(ZoneOffset.UTC).year)
        assertTrue(pinned.isEmpty())
    }

    @Test
    fun aBioShowsWhereItsLinksLeadAndMarksItsMentions() {
        val (user, _) = XParser.profile(fixture("user_modern.json"))!!
        val runs = XText.bio(user)
        assertEquals(
            listOf(XRun.Link("github.com/Teskann/QuaX", "http://github.com/Teskann/QuaX"), XRun.Link("flutter.dev", "https://flutter.dev")),
            runs.filterIsInstance<XRun.Link>(),
        )
        assertEquals(listOf(XRun.Mention("@jack", "jack")), runs.filterIsInstance<XRun.Mention>())
        assertTrue(runs.none { "t.co" in it.text }, "no t.co address is left in the bio")
        assertTrue(runs.first().text.startsWith("🧪 Throwaway account"))
    }

    @Test
    fun anOlderUserResultReadsFromLegacy() {
        val (user, _) = XParser.profile(fixture("user_legacy.json"))!!
        assertEquals("783214", user.id)
        assertEquals("X", user.screenName)
        assertEquals(60809772L, user.followers)
    }

    @Test
    fun aTweetResultBecomesAPost() {
        val post = XParser.post(fixture("tweet_result.json"))!!
        assertEquals("2079275370742677949", post.id)
        assertTrue(post.text.startsWith("more upgrades to come"))
        assertNotNull(post.createdAt)
    }

    @Test
    fun aConversationReadsAPreviewAsItsPostAndKeepsTheWithheldReplysPlace() {
        // As XTA reads it since QuaX #188: a subscriber preview is the post, an exclusive reply a gap.
        val page = XParser.conversation(fixture("tweet_detail_subscriber_preview.json"))
        assertEquals(listOf("2105589900078641579"), page.threads.flatMap { it.posts }.map { it.id })
        assertEquals(listOf("2105590666927513924"), page.threads.flatMap { it.withheld }.map { it.id })
        assertNotNull(page.bottomCursor)
    }

    @Test
    fun aGuestTimelineEntriesPageReads() {
        val page = XParser.entries(fixture("user_tweets_add_entries.json")["entries"].list)
        assertTrue(page.threads.isNotEmpty())
        assertTrue(page.threads.all { thread -> thread.posts.all { it.author.screenName.isNotEmpty() } })
    }

    @Test
    fun textRunsCutByCodePointsUnescapeAndDropTheMediaLink() {
        // "😀 hi @maya &amp; #kiln $NVDA https://t.co/abc https://t.co/pic"
        val text = "😀 hi @maya &amp; #kiln \$NVDA https://t.co/abc https://t.co/pic"
        val post = XPost(
            id = "1",
            author = XUser("9", "zuck", "Mark"),
            createdAt = null,
            text = text,
            entities = listOf(
                XEntity.Mention(5, 10, "maya"),
                XEntity.Hashtag(17, 22, "kiln"),
                XEntity.Cashtag(23, 28, "NVDA"),
                XEntity.Link(29, 45, "https://t.co/abc", "https://example.com/a", "example.com/a"),
                XEntity.Link(46, 62, "https://t.co/pic", "https://x.com/zuck/status/1/photo/1", "pic.x.com/x"),
            ),
            media = listOf(XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/a.jpg", 100, 100, shortUrl = "https://t.co/pic")),
        )
        assertEquals(
            listOf(
                XRun.Text("😀 hi "),
                XRun.Mention("@maya", "maya"),
                XRun.Text(" & "),
                XRun.Hashtag("#kiln", "kiln"),
                XRun.Text(" "),
                XRun.Cashtag("\$NVDA", "NVDA"),
                XRun.Text(" "),
                XRun.Link("example.com/a", "https://example.com/a"),
            ),
            XText.runs(post),
        )
    }

    @Test
    fun aReplysLeadingMentionsOutsideTheDisplayRangeAreNotShown() {
        val post = XPost(
            id = "2",
            author = XUser("9", "zuck", "Mark"),
            createdAt = null,
            text = "@maya thanks!",
            displayStart = 6,
            entities = listOf(XEntity.Mention(0, 5, "maya")),
        )
        assertEquals(listOf<XRun>(XRun.Text("thanks!")), XText.runs(post))
    }
}
