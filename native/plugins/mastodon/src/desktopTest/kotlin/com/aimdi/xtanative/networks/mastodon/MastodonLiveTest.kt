package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live mastodon.social, read without an account as XTA reads it: a public
 * account looked up and its statuses read. Skipped unless RUN_LIVE=true.
 */
class MastodonLiveTest {
    private val instance = "https://mastodon.social"

    @Test
    fun aPublicAccountsStatusesAreReadFromMastodonSocial() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live mastodon.social")
        val client = MastodonClient(OkHttpXHttp())
        val profile = client.lookup(instance, "Mastodon")
        assertEquals("mastodon@mastodon.social", profile.acct)
        assertTrue(profile.followersCount > 100_000, "@Mastodon has its followers: ${profile.followersCount}")

        val page = client.statuses(instance, profile.id)
        assertTrue(page.items.isNotEmpty(), "@Mastodon has public statuses")
        assertTrue(page.items.all { it.url.startsWith("https://") && it.publishedAt != null })
        assertTrue(page.items.all { it.boosted || it.acct == "mastodon@mastodon.social" })
        println("LIVE @Mastodon ${profile.followersCount} followers, ${page.items.size} statuses, next ${page.next}")
    }

    /** What else a guest gets from live mastodon.social, written to the log and never failed on. */
    @Test
    fun whatAGuestCanReadIsLogged() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live mastodon.social")
        val client = MastodonClient(OkHttpXHttp())
        val instances = listOf(instance)
        probe("profile with pins") { client.profileAnywhere(instances, "Gargron@mastodon.social").let { "${it.posts.size} posts, ${it.pinnedIds.size} pinned" } }
        probe("local timeline") { client.publicTimeline(instance, local = true).items.size.toString() + " posts" }
        probe("hashtag") { client.tagTimeline(instance, "photography").items.size.toString() + " posts" }
        probe("trending") { client.explore(instances).let { "${it.tags.size} tags, ${it.posts.size} posts" } }
        probe("search") { client.search(instance, "mastodon").let { "${it.accounts.size} accounts, ${it.posts.size} posts, ${it.tags.size} tags" } }
        probe("thread") {
            val seed = client.statuses(instance, client.lookup(instance, "Gargron").id).items.first()
            client.thread(instance, seed).let { "${it.ancestors.size} above, ${it.descendants.size} below" }
        }
    }

    private suspend fun probe(what: String, read: suspend () -> String) {
        val outcome = runCatching { read() }.fold({ it }, { "refused, ${it::class.simpleName}: ${it.message?.take(160)}" })
        println("LIVE mastodon $what: $outcome")
    }
}
