package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live Bluesky, as XTA's test/live/sources_smoke_test.dart reads it: a public
 * profile and its author feed from the public AppView, with no account.
 * Skipped unless RUN_LIVE=true.
 */
class BlueskyLiveTest {
    @Test
    fun aPublicProfileAndItsAuthorFeedReadFromThePublicAppView() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Bluesky")
        val client = BlueskyClient(OkHttpXHttp())

        val profile = client.profile("bsky.app")
        assertEquals("bsky.app", profile.handle)
        assertTrue(profile.did.startsWith("did:plc:"), "the account has its DID: ${profile.did}")
        assertTrue(profile.followersCount > 1_000_000, "@bsky.app has its followers: ${profile.followersCount}")

        val feed = client.authorFeed(profile.actor, limit = 5)
        assertTrue(feed.items.isNotEmpty(), "the author feed has posts")
        assertTrue(feed.items.all { it.url.startsWith("https://bsky.app/profile/") && it.uri.startsWith("at://") })
        println("LIVE Bluesky @${profile.handle}: ${profile.followersCount} followers, ${feed.items.size} posts, next page ${feed.next != null}")
    }
}
