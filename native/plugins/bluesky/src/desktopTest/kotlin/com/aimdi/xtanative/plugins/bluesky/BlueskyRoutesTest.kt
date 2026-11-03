package com.aimdi.xtanative.plugins.bluesky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlueskyRoutesTest {
    @Test
    fun aPostOpensByItsUriAndComesBack() {
        val uri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.post/3l6oveex3ii2l"
        assertEquals(BlueskyRoute.Post(PostTarget.Uri(uri)), BlueskyRoutes.parse(BlueskyRoutes.post(uri)))
    }

    @Test
    fun aPostLinkOpensItsThreadByAuthorAndKey() {
        val route = BlueskyRoutes.forUrl("https://bsky.app/profile/bsky.app/post/3l6oveex3ii2l")
        assertEquals("post?actor=bsky.app&rkey=3l6oveex3ii2l", route)
        assertEquals(BlueskyRoute.Post(PostTarget.Key("bsky.app", "3l6oveex3ii2l")), BlueskyRoutes.parse(route!!))
    }

    @Test
    fun aProfileLinkOpensTheProfile() {
        val route = BlueskyRoutes.forUrl("https://bsky.app/profile/maya.bsky.social")
        assertEquals(BlueskyRoute.Profile("maya.bsky.social"), BlueskyRoutes.parse(route!!))
        assertEquals(BlueskyRoute.Profile("did:plc:maya"), BlueskyRoutes.parse(BlueskyRoutes.profile("did:plc:maya")))
    }

    @Test
    fun otherLinksAreNotBlueskys() {
        listOf(
            "https://example.com/profile/maya.bsky.social",
            "https://x.com/maya/status/1",
            "http://bsky.app/profile/maya.bsky.social",
            "at://did:plc:maya/app.bsky.feed.post/3l6",
            "https://bsky.app/",
        ).forEach { assertNull(BlueskyRoutes.forUrl(it), it) }
    }

    @Test
    fun unknownRoutesOpenNothing() {
        listOf("", "post", "post?uri=https%3A%2F%2Fexample.com", "post?actor=maya", "profile", "feed?uri=at%3A%2F%2Fx").forEach {
            assertNull(BlueskyRoutes.parse(it), it)
        }
    }

    @Test
    fun aRoutesValuesSurviveTheirEscaping() {
        val actor = "did:web:example.com:a b&c=d"
        assertEquals(BlueskyRoute.Profile(actor), BlueskyRoutes.parse(BlueskyRoutes.profile(actor)))
    }
}
