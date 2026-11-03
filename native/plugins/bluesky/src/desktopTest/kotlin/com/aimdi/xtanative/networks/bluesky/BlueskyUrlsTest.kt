package com.aimdi.xtanative.networks.bluesky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlueskyUrlsTest {
    @Test
    fun anAppViewIsMadeUsableFromABareHostOrAFullAddress() {
        assertEquals("https://public.api.bsky.app", BlueskyUrls.appView("public.api.bsky.app"))
        assertEquals("https://public.api.bsky.app", BlueskyUrls.appView("https://public.api.bsky.app/"))
        assertEquals("https://example.org", BlueskyUrls.appView("  https://example.org  "))
        assertEquals("https://example.org", BlueskyUrls.appView("https://EXAMPLE.org:443/?q=1#top"))
        assertEquals("http://localhost:2584/xrpc-proxy", BlueskyUrls.appView("http://localhost:2584/xrpc-proxy//"))
    }

    @Test
    fun anUnusableAppViewFallsBackToBlueskysOwn() {
        assertNull(BlueskyUrls.appView(""))
        assertNull(BlueskyUrls.appView("ftp://example.org"))
        assertNull(BlueskyUrls.appView("not a url"))
        assertEquals(Bluesky.DEFAULT_APP_VIEW, BlueskyUrls.appViewOrDefault(null))
        assertEquals(Bluesky.DEFAULT_APP_VIEW, BlueskyUrls.appViewOrDefault(""))
        assertEquals(Bluesky.DEFAULT_APP_VIEW, BlueskyUrls.appViewOrDefault("ftp://nope"))
        assertEquals("https://my.appview.example", BlueskyUrls.appViewOrDefault("https://my.appview.example"))
    }

    @Test
    fun handlesComeBareWithAnAtOrInAProfileLink() {
        assertEquals("alice.bsky.social", BlueskyUrls.handle("alice.bsky.social"))
        assertEquals("alice.bsky.social", BlueskyUrls.handle("@Alice.bsky.social"))
        assertEquals("alice.bsky.social", BlueskyUrls.handle("  @alice.bsky.social "))
        assertEquals("alice.bsky.social", BlueskyUrls.handle("https://bsky.app/profile/alice.bsky.social"))
        assertEquals("alice.bsky.social", BlueskyUrls.handle("https://www.bsky.app/profile/alice.bsky.social/post/abc"))
        assertEquals("jay.bsky.team", BlueskyUrls.handle("jay.bsky.team"))
        assertEquals("did:plc:z72i7hdynmk6r22z27h6tvur", BlueskyUrls.handle("DID:PLC:z72i7hdynmk6r22z27h6tvur"))
        assertEquals("did:plc:abc234", BlueskyUrls.handle("did:plc:abc234"))
    }

    @Test
    fun whatIsNoHandleIsRefused() {
        listOf("", "   ", "@", "elios", "nodot", "two words.bsky.social", "https://example.org/nothing", "did:web:example.com", "-bad-.example")
            .forEach { assertNull(BlueskyUrls.handle(it), it) }
    }

    @Test
    fun aPostsWebLinkUsesItsHandleElseTheDidInItsUri() {
        val uri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.post/3mqafridzgk2e"
        assertEquals("https://bsky.app/profile/bsky.app/post/3mqafridzgk2e", BlueskyUrls.webUrl("bsky.app", uri))
        assertEquals("https://bsky.app/profile/did:plc:z72i7hdynmk6r22z27h6tvur/post/3mqafridzgk2e", BlueskyUrls.webUrl("", uri))
        assertNull(BlueskyUrls.webUrl("alice.test", "at://did:plc:abc/app.bsky.graph.list/list"), "a list is no post")
        assertNull(BlueskyUrls.webUrl("bsky.app", "at://did:plc:x/app.bsky.feed.post"))
        assertNull(BlueskyUrls.webUrl("", "at://did:web:example.com/app.bsky.feed.post/abc"), "neither a handle nor a did:plc to link by")
    }

    @Test
    fun aRecordKeyIsTheLastOfThreeParts() {
        assertEquals("whats-hot", BlueskyUrls.rkeyOf(Bluesky.DISCOVER_FEED_URI))
        assertNull(BlueskyUrls.rkeyOf("not-an-at-uri"))
        assertNull(BlueskyUrls.rkeyOf("at://did:plc:x/app.bsky.feed.post/.."))
        assertNull(BlueskyUrls.rkeyOf("at://did:plc:x/app.bsky.feed.post/a b"))
        assertNull(BlueskyUrls.rkeyOf("at://did:plc:x/app.bsky.feed.post/a/b"))
    }

    @Test
    fun listsFeedsAndStarterPacksAreNamedByLinkOrUri() {
        assertEquals(BlueskyRef.Web("alice.bsky.social", "3abc"), BlueskyUrls.listRef("https://bsky.app/profile/alice.bsky.social/lists/3abc"))
        assertEquals(BlueskyRef.AtUri("at://did:plc:abc/app.bsky.graph.list/3abc"), BlueskyUrls.listRef(" at://did:plc:abc/app.bsky.graph.list/3abc "))
        assertNull(BlueskyUrls.listRef("https://bsky.app/profile/alice.bsky.social"))
        assertEquals(BlueskyRef.Web("bsky.app", "whats-hot"), BlueskyUrls.feedRef("https://bsky.app/profile/bsky.app/feed/whats-hot"))
        assertEquals(BlueskyRef.AtUri(Bluesky.DISCOVER_FEED_URI), BlueskyUrls.feedRef(Bluesky.DISCOVER_FEED_URI))
        assertNull(BlueskyUrls.feedRef("https://bsky.app/profile/bsky.app/lists/x"), "a list link is no feed")
        assertEquals(BlueskyRef.Web("alice.bsky.social", "3abc"), BlueskyUrls.starterPackRef("https://bsky.app/starter-pack/alice.bsky.social/3abc"))
        assertEquals(BlueskyRef.AtUri("at://did:plc:abc/app.bsky.graph.starterpack/3abc"), BlueskyUrls.starterPackRef("at://did:plc:abc/app.bsky.graph.starterpack/3abc"))
        assertNull(BlueskyUrls.starterPackRef("https://go.bsky.app/xyz"), "short links are not followed")
        assertNull(BlueskyUrls.starterPackRef("https://bsky.app/profile/alice.bsky.social"))
        assertNull(BlueskyUrls.starterPackRef(""))
    }

    @Test
    fun aSearchNamesAPostByItsLinkOrItsUri() {
        assertEquals(BlueskyPostTarget("maya.bsky.social", "root"), BlueskyUrls.searchPostTarget("https://bsky.app/profile/maya.bsky.social/post/root?source=share"))
        assertEquals(BlueskyPostTarget("maya.bsky.social", "root"), BlueskyUrls.searchPostTarget("https://bsky.app/profile/maya.bsky.social/post/root/"))
        assertEquals(BlueskyPostTarget("did:plc:maya", "root"), BlueskyUrls.searchPostTarget("at://did:plc:maya/app.bsky.feed.post/root"))
        assertEquals(BlueskyPostTarget("did:web:example.com", "root"), BlueskyUrls.searchPostTarget("at://did:web:example.com/app.bsky.feed.post/root"))
    }

    @Test
    fun aSearchNamesNoPostByAnythingLooser() {
        listOf(
            "https://bsky.app/profile/maya.bsky.social",
            "https://evil.example/profile/maya.bsky.social/post/root",
            "https://bsky.app.evil.example/profile/maya.bsky.social/post/root",
            "https://user@bsky.app/profile/maya.bsky.social/post/root",
            "https://bsky.app:8443/profile/maya.bsky.social/post/root",
            "http://bsky.app/profile/maya.bsky.social/post/root",
            "https://bsky.app/profile/maya.bsky.social/post/root/extra",
            "at://did:plc:maya/app.bsky.graph.list/root",
            "at://did:plc:maya/app.bsky.feed.post/..",
            "at://did:plc:maya/app.bsky.feed.post/root?query",
            "sketches",
        ).forEach { assertNull(BlueskyUrls.searchPostTarget(it), it) }
    }

    @Test
    fun aSearchNamesAnAccountByDidProfileLinkOrHandleButNotByAPostLink() {
        assertNull(BlueskyUrls.searchActor("https://bsky.app/profile/maya.bsky.social/post/root"))
        assertEquals("maya.bsky.social", BlueskyUrls.searchActor("@maya.bsky.social"))
        assertEquals("did:web:example.com", BlueskyUrls.searchActor("https://bsky.app/profile/did:web:example.com"))
        assertEquals("did:plc:Mixed", BlueskyUrls.searchActor(" did:plc:Mixed "), "a DID is kept as it is written")
        assertNull(BlueskyUrls.searchActor("localhost:3000"))
        assertNull(BlueskyUrls.searchActor("maya"))
    }
}
