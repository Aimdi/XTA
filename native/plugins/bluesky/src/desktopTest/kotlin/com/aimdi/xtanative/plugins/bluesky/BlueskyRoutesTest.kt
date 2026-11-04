package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
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
    fun aProfileLinkOpensTheProfileAsXtaReadsIt() {
        listOf(
            "https://bsky.app/profile/maya.bsky.social",
            "http://bsky.app/profile/maya.bsky.social",
            "https://www.bsky.app/profile/maya.bsky.social/lists/3k",
        ).forEach { url -> assertEquals(BlueskyRoute.Profile("maya.bsky.social"), BlueskyRoutes.parse(BlueskyRoutes.forUrl(url)!!), url) }
        assertEquals(BlueskyRoute.Profile("did:plc:maya"), BlueskyRoutes.parse(BlueskyRoutes.profile("did:plc:maya")))
    }

    @Test
    fun otherLinksAreNotBlueskys() {
        listOf(
            "https://example.com/profile/maya.bsky.social",
            "https://x.com/maya/status/1",
            "at://did:plc:maya/app.bsky.feed.post/3l6",
            "https://bsky.app/",
            "https://bsky.app/search?q=cats",
        ).forEach { assertNull(BlueskyRoutes.forUrl(it), it) }
    }

    @Test
    fun theOtherScreensComeBackFromTheirRoutes() {
        val uri = "at://did:plc:maya/app.bsky.feed.post/1"
        assertEquals(BlueskyRoute.People("did:plc:maya", PeopleKind.FOLLOWERS), BlueskyRoutes.parse(BlueskyRoutes.people("did:plc:maya", PeopleKind.FOLLOWERS)))
        assertEquals(BlueskyRoute.Activity(uri, "https://bsky.app/x", quotes = true), BlueskyRoutes.parse(BlueskyRoutes.activity(uri, "https://bsky.app/x", quotes = true)))
        assertEquals(BlueskyRoute.Activity(uri, "", quotes = false), BlueskyRoutes.parse(BlueskyRoutes.activity(uri, "", quotes = false)))
        assertEquals(BlueskyRoute.Search("#cats & dogs", "maya.bsky.social", BlueskySearchKind.POSTS), BlueskyRoutes.parse(BlueskyRoutes.search("#cats & dogs", "maya.bsky.social", BlueskySearchKind.POSTS)))
        assertEquals(BlueskyRoute.Search("", "", BlueskySearchKind.PEOPLE), BlueskyRoutes.parse(BlueskyRoutes.search()))
        assertEquals(BlueskyRoute.Import(ImportKind.LIST, "at://did:plc:maya/app.bsky.graph.list/l"), BlueskyRoutes.parse(BlueskyRoutes.import(ImportKind.LIST, "at://did:plc:maya/app.bsky.graph.list/l")))
        assertEquals(BlueskyRoute.Feeds, BlueskyRoutes.parse(BlueskyRoutes.FEEDS))
    }

    @Test
    fun unknownRoutesOpenNothing() {
        listOf(
            "", "post", "post?uri=https%3A%2F%2Fexample.com", "post?actor=maya", "profile", "feed?uri=at%3A%2F%2Fx",
            "people?actor=maya&kind=friends", "reposts?uri=https%3A%2F%2Fx", "import?kind=everything",
        ).forEach { assertNull(BlueskyRoutes.parse(it), it) }
    }

    @Test
    fun aRoutesValuesSurviveTheirEscaping() {
        val actor = "did:web:example.com:a b&c=d"
        assertEquals(BlueskyRoute.Profile(actor), BlueskyRoutes.parse(BlueskyRoutes.profile(actor)))
    }
}
