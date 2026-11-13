package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.ui.subscriptions.GroupAddSources.BLUESKY
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources.MASTODON
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources.REDDIT
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources.RSS
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources.SUBSTACK
import com.aimdi.xtanative.ui.subscriptions.GroupAddSources.THREADS
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupAddSourcesTest {
    private fun of(text: String) = GroupAddSources.pluginsFor(text)

    @Test
    fun blankTextIsNothing() {
        assertEquals(emptyList(), of("   "))
    }

    @Test
    fun aBareWordCouldBeASubredditAThreadsHandleOrANewsletter() {
        assertEquals(listOf(REDDIT, THREADS, SUBSTACK), of("flutter"))
    }

    @Test
    fun typedNamesGoWhereTheirShapeSays() {
        assertEquals(listOf(REDDIT), of("r/dartlang"))
        assertEquals(listOf(REDDIT), of("/R/dartlang"))
        assertEquals(listOf(BLUESKY), of("did:plc:abc234"))
        assertEquals(listOf(MASTODON), of("@alice@example.social"))
        assertEquals(listOf(BLUESKY, SUBSTACK), of("alice.bsky.social"))
        assertEquals(listOf(BLUESKY, SUBSTACK), of("@platformer.news"))
    }

    @Test
    fun aPastedAddressNamesOneNetwork() {
        assertEquals(listOf(REDDIT), of("https://www.reddit.com/r/dartlang/"))
        assertEquals(listOf(THREADS), of("https://www.threads.com/@zuck"))
        assertEquals(listOf(BLUESKY), of("https://bsky.app/profile/alice.bsky.social"))
        assertEquals(listOf(MASTODON), of("https://mastodon.social/@Gargron"))
        assertEquals(listOf(MASTODON), of("https://example.social/users/alice"))
        assertEquals(listOf(RSS), of("https://example.com/feed.xml"))
        assertEquals(listOf(RSS), of("https://example.com/blog/rss"))
        assertEquals(listOf(SUBSTACK), of("https://www.platformer.news"))
    }
}
