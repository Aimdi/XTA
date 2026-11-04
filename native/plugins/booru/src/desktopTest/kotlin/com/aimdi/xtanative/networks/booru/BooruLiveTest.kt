package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live Danbooru, read as a guest the way XTA reads it: a General-rated tag search, one post's
 * detail and the tag autocomplete. Skipped unless RUN_LIVE=true.
 */
class BooruLiveTest {
    @Test
    fun aSafeSearchOnDanbooruReadsAPostsDetailAndTheAutocomplete() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Danbooru")
        val client = BooruClient(OkHttpXHttp(), settings = { BooruSettings(maxRating = BooruRating.GENERAL) })

        val page = client.search("scenery", limit = 10)
        assertTrue(page.posts.isNotEmpty(), "a General search for scenery finds posts")
        assertTrue(page.posts.all { it.rating == BooruRating.GENERAL }, "the cap was sent as rating:g")
        val first = page.posts.first()

        val post = assertNotNull(client.postAt(BooruPostRef(first.host, first.engine, first.id)), "the post read by its id")
        assertEquals(first.id, post.id)
        assertTrue(post.hasFile && post.width > 0 && post.height > 0, "it has a file and its size")
        assertTrue(post.tagCategories.values.any { it == BooruTagCategory.GENERAL }, "Danbooru names its tags' kinds")
        val tags = client.tagInfo(post)
        assertTrue(tags.values.any { (it.postCount ?: 0) > 0 }, "the tag lookup counts the posts of each tag")

        val suggestions = client.suggestTags("miku")
        assertTrue(suggestions.any { it.name == "hatsune_miku" }, "autocomplete finds a word inside a name: ${suggestions.map { it.name }}")
        println("LIVE Danbooru: ${page.posts.size} posts, post ${post.id} ${post.width}x${post.height} with ${tags.size} tags; miku -> ${suggestions.first().name}")
    }
}
