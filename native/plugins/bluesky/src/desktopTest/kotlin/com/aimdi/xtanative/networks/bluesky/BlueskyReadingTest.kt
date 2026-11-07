package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What XTA's Bluesky screen keeps in its preferences: the tab and the filters, and the feed or list open on each AppView. */
class BlueskyReadingTest {
    /** As XTA's reader store writes it, its reading place and posts with it. */
    private val xta = """{"version":1,"source":"https://public.api.bsky.app","tab":3,""" +
        """"options":{"following":{"content":"videos","order":"newest","hideReplies":true,"hideReposts":false},""" +
        """"likes":{"content":"all","order":"oldest","hideReplies":false,"hideReposts":true}},"anchor":"at://x","leading":-12.5,"posts":[]}"""

    @Test
    fun xtasTabAndFiltersAreRead() {
        val reading = BlueskyReadingPrefs.parse(xta)
        assertEquals(BlueskyTab.LIKED, reading.tab)
        assertEquals(BlueskyReaderFilter(content = BlueskyReaderFilter.Content.VIDEOS, order = BlueskyReaderFilter.Order.NEWEST, hideReplies = true), reading.following)
        assertEquals(BlueskyReaderFilter(order = BlueskyReaderFilter.Order.OLDEST, hideReposts = true), reading.likes)
    }

    @Test
    fun writingKeepsWhatElseXtaWroteThere() {
        val written = BlueskyReadingPrefs.write(xta, BlueskyReading(tab = BlueskyTab.DISCOVER, following = BlueskyReaderFilter(query = "words", hideReposts = true)))
        val json = J.parse(written)
        assertEquals(1L, json["tab"].long)
        assertEquals("at://x", json["anchor"].string, "XTA's reading place stays for XTA")
        assertEquals("https://public.api.bsky.app", json["source"].string)
        assertEquals(BlueskyReading(BlueskyTab.DISCOVER, BlueskyReaderFilter(hideReposts = true)), BlueskyReadingPrefs.parse(written), "the words are not kept")
    }

    @Test
    fun anUnknownOrDamagedValueReadsAsTheDefaults() {
        listOf("", "nonsense", """{"version":2,"tab":1}""", """{"version":1,"tab":9,"options":{"following":{"content":"films"}}}""").forEach { text ->
            val reading = BlueskyReadingPrefs.parse(text)
            assertEquals(BlueskyReaderFilter(), reading.following, text)
        }
        assertEquals(BlueskyTab.LIKED, BlueskyReadingPrefs.parse("""{"version":1,"tab":9}""").tab, "a tab past the last is the last, as XTA clamps it")
        assertEquals("""{"version":1,"tab":0,"options":{"following":{"content":"all","order":"feed","hideReplies":false,"hideReposts":false},"likes":{"content":"all","order":"feed","hideReplies":false,"hideReposts":false}}}""", BlueskyReadingPrefs.write("", BlueskyReading()))
    }

    @Test
    fun theFeedOpenIsRememberedPerAppView() {
        val one = BlueskySnapshot.withSelection("{}", "https://public.api.bsky.app", BlueskySelection("at://did:plc:a/app.bsky.feed.generator/cats", "Cats"))
        val two = BlueskySnapshot.withSelection(one, "https://appview.example", BlueskySelection("at://did:plc:b/app.bsky.feed.generator/dogs", "Dogs"))
        assertEquals("Cats", BlueskySnapshot.selection(two, "https://public.api.bsky.app")?.name)
        assertEquals("Dogs", BlueskySnapshot.selection(two, "https://appview.example")?.name)
        assertNull(BlueskySnapshot.selection(two, "https://other.example"))
        assertNull(BlueskySnapshot.selection("""{"https://x": {"uri": "https://not-at", "name": "n"}}""", "https://x"))
    }

    @Test
    fun pinsReadAsXtaWroteThem() {
        val feeds = """[{"uri":"at://did:plc:a/app.bsky.feed.generator/cats","displayName":"Cats","description":"","avatarUrl":null,"creatorHandle":"a.bsky.social"},{"uri":""},"x"]"""
        assertEquals(listOf("Cats"), BlueskySnapshot.feeds(feeds).map { it.displayName })
        assertEquals(BlueskySnapshot.feeds(feeds), BlueskySnapshot.feeds(BlueskySnapshot.feedsJson(BlueskySnapshot.feeds(feeds))))
        val lists = """[{"uri":"at://did:plc:a/app.bsky.graph.list/l","name":"Friends","itemCount":2,"description":"","avatarUrl":null,"creatorHandle":null}]"""
        assertEquals(BlueskySnapshot.lists(lists), BlueskySnapshot.lists(BlueskySnapshot.listsJson(BlueskySnapshot.lists(lists))))
    }

    @Test
    fun followingsPlaceIsKeptForItsAppViewAsXtaKeepsIt() {
        val source = "https://public.api.bsky.app"
        val posts = (1..3).map { bluePost("p$it") }
        val written = BlueskyReadingPrefs.write(xta, source, BlueskyReadPoint(posts, uriOf("p2"), -40.0))
        assertEquals(BlueskyTab.LIKED, BlueskyReadingPrefs.parse(written).tab, "the tab and filters stay")
        val point = BlueskyReadingPrefs.point(written, source)!!
        assertEquals(posts.map { it.uri }, point.posts.map { it.uri })
        assertEquals(uriOf("p2"), point.anchor)
        assertEquals(-40.0, point.leading)
        assertNull(BlueskyReadingPrefs.point(written, "https://appview.example"), "another AppView's place is not restored")
        assertNull(BlueskyReadingPrefs.point(xta, source), "a place without posts restores nothing")
        assertNull(BlueskyReadingPrefs.point(BlueskyReadingPrefs.write(written, source, null), source))
        val far = """{"version":1,"source":"$source","leading":1e9,"posts":[${BlueskySnapshot.json(posts[0])}]}"""
        assertEquals(10_000.0, BlueskyReadingPrefs.point(far, source)!!.leading)
        assertEquals(posts[0].uri, BlueskyReadingPrefs.point(far, source)!!.anchor, "no anchor is the first post")
    }

    @Test
    fun aPlaceKeepsTwoDozenBeforeItsAnchorAndAtMostItsLimit() {
        val posts = (0 until 300).map { bluePost("p$it") }
        val window = BlueskyReadingPrefs.window(posts, uriOf("p100"))
        assertEquals(uriOf("p76"), window.first().uri)
        assertEquals(BlueskyReadingPrefs.POSTS, window.size)
        assertEquals(uriOf("p0"), BlueskyReadingPrefs.window(posts, uriOf("p3")).first().uri)
    }

    @Test
    fun onlyPostsByOrRepostedByFollowedAccountsAreRestored() {
        val mine = bluePost("a")
        val reposted = bluePost("b", did = "did:plc:other", repostedByHandle = "maya.bsky.social")
        val gone = bluePost("c", did = "did:plc:gone")
        val kept = BlueskyReadingPrefs.kept(listOf(mine, reposted, gone)) { it == "maya.bsky.social" || it == "did:plc:maya" }
        assertEquals(listOf(mine, reposted), kept)
    }

    @Test
    fun aRestoredPlaceMatchesFollowsByDidOrHandleAndEmptyNamesMatchNothing() {
        val maya = BlueskyFollow("did:plc:maya", "maya.bsky.social", "Maya", null, 1)
        val posts = listOf(
            bluePost("original"),
            bluePost("renamed", handle = "renamed.example"),
            bluePost("boosted", did = "did:plc:other", handle = "other.example", repostedByDid = "did:plc:maya"),
            bluePost("boost-handle", did = "did:plc:other", handle = "other.example", repostedByHandle = "MAYA.BSKY.SOCIAL"),
            bluePost("unfollowed", did = "did:plc:other", handle = "other.example"),
        )
        assertEquals(listOf("original", "renamed", "boosted", "boost-handle"), BlueskyReadingPrefs.kept(posts, maya::isOf).map { it.text })
        assertEquals(emptyList(), BlueskyReadingPrefs.kept(posts) { false })
        val nameless = BlueskyFollow("did:plc:elsewhere", "", "", null, 1)
        assertEquals(emptyList(), BlueskyReadingPrefs.kept(listOf(bluePost("unrelated", did = "", handle = "")), nameless::isOf))
    }
}
