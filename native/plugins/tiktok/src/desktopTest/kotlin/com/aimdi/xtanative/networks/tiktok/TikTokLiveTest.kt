package com.aimdi.xtanative.networks.tiktok

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live tiktok.com as a guest, as XTA's test/live/tiktok_search_smoke_test.dart
 * reads it: trending searches, the discover page's people, and a search for
 * "nba" that finds @nba with suggestions, all without signing a request.
 * Skipped unless RUN_LIVE=true.
 */
class TikTokLiveTest {
    @Test
    fun aGuestSearchFindsPeopleAndSuggestionsWithoutSigning() = runBlocking<Unit> {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live tiktok.com")
        val client = TikTokClient(OkHttpXHttp())

        val trending = client.trendingQueries()
        assertTrue(trending.isNotEmpty(), "TikTok suggests trending searches")

        val discover = client.discoverUsers()
        assertTrue(discover.isNotEmpty(), "the discover page lists people")
        assertTrue(discover.first().uniqueId.isNotEmpty())

        val page = client.search("nba")
        assertTrue("nba" in page.users.map { it.uniqueId.lowercase() }, "found ${page.users.map { it.uniqueId }}")
        assertTrue(page.suggestions.isNotEmpty())
        println("LIVE TikTok: ${trending.size} trending, ${discover.size} on discover, ${page.users.size} people and ${page.posts.size} videos for nba")
    }
}
