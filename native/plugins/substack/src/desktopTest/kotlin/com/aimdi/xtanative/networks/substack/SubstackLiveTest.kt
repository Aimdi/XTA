package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live Substack, read as XTA reads it: one public publication's archive, then
 * one of its posts whole, through its public JSON. Skipped unless RUN_LIVE=true.
 * A read that fails is logged with what each address it asked answered, so the
 * run names the route that changed. Substack's Cloudflare turns some networks
 * away, GitHub's runners among them, with a challenge (403) on every route;
 * the test is skipped there too, saying so, as Reddit's is, since that answer
 * says nothing about the reader. Where only the API turns it away, the
 * archive comes from the RSS feed, as in the app, and the test reads that.
 */
class SubstackLiveTest {
    @Test
    fun aPublicPublicationsArchiveAndOneOfItsPostsAreRead(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live Substack")
        val client = SubstackClient(OkHttpXHttp())
        val publication = logged("publication https://on.substack.com") { client.publication("https://on.substack.com") }
        assertTrue(publication.name.isNotBlank(), "the publication names itself: $publication")

        val archive = logged("archive of ${publication.baseUrl}") { client.posts(publication, limit = 5) }
        assertTrue(archive.items.isNotEmpty(), "the archive lists posts")
        // Where Substack's API turns a network away, the archive is read from the RSS feed, whose
        // posts come with their bodies; the API's listing has none. Either is a reading.
        assertTrue(archive.items.all { it.title.isNotEmpty() && it.slug.isNotEmpty() }, "every listed post has a title and an address")
        println("LIVE archive read from ${if (archive.items.any { it.bodyHtml != null }) "the RSS feed" else "the API"}")

        val listed = archive.items.first()
        val post = logged("post ${listed.slug}") { client.post(publication, listed.slug) }
        assertTrue(post.key == listed.key, "one post, whichever endpoint gave it: ${post.key} vs ${listed.key}")
        assertTrue(!post.bodyHtml.isNullOrBlank() || post.isPaywalled, "a free post comes whole")
        val article = post.bodyHtml?.takeIf { it.isNotBlank() }?.let(SubstackArticle::parse)
        println(
            "LIVE ${publication.displayName}: ${archive.items.size} posts, \"${post.title}\" " +
                "${article?.blocks?.size ?: 0} blocks, ${SubstackReading.readMinutes(post, article?.plainText)} min",
        )
    }

    /** [read], with its failure logged whole (the routes asked and their answers) before it fails the test. */
    private suspend fun <T> logged(what: String, read: suspend () -> T): T = try {
        read()
    } catch (e: Exception) {
        println("LIVE substack: $what failed: ${e.javaClass.simpleName}: ${e.message}")
        assumeFalse(e is NetworkRequestException && e.code == 403, "Substack refuses this network: ${e.message}")
        throw e
    }
}
