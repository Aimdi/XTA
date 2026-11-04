package com.aimdi.xtanative.core.x.live

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Live x.com, as XTA's test/live/x_bootstrap_smoke_test.dart reads it: the
 * request signer derived from x.com's own pages with no session, then @X's
 * profile read as a guest. Skipped unless RUN_LIVE=true.
 */
class XLiveTest {
    @Test
    fun aSignatureDerivedFromLiveXComReadsAPublicProfile() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live x.com")
        val http = OkHttpXHttp()
        val signers = XSigners(SigningBootstrap(http))
        val client = XClient(http, accounts = { emptyList() }, signers = signers, guest = XGuest(http))
        client.refreshEndpoints()

        val signer = signers.forSession(null)
        assertEquals(48, signer.keyBytes.size, "the key comes from x.com's twitter-site-verification meta")
        assertTrue(signer.animationKey.isNotEmpty())

        val (user, _) = client.profile("X")
        assertEquals("X", user.screenName)
        assertTrue((user.followers ?: 0) > 1_000_000, "@X has its followers: ${user.followers}")
        println("LIVE @X ${user.name}, ${user.followers} followers, key ${signer.keyBytes.size} bytes")
    }

    /**
     * What else a guest gets from live X, written to the log and never failed
     * on: whether reading without an account could be more than profiles.
     */
    @Test
    fun whatAGuestCanReadIsLogged() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live x.com")
        val http = OkHttpXHttp()
        val client = XClient(http, accounts = { emptyList() }, signers = XSigners(SigningBootstrap(http)), guest = XGuest(http))
        client.refreshEndpoints()
        val (user, _) = client.profile("X")
        probe("posts") { client.userPosts(user.id).threads.size }
        probe("reposts") { client.userReposts(user.id).threads.size }
        probe("replies") { client.userReplies(user.id).threads.size }
        probe("media") { client.userMedia(user.id).threads.size }
        probe("conversation") { client.conversation("20").threads.size }
        probe("search") { client.search("from:X").threads.size }
        probe("following") { client.following(user.id, count = 20).users.size }
        probe("reposters") { client.reposters("20").users.size }
        probe("people search") { client.searchUsers("flutter").users.size }
        probe("community") { client.community("1472249315724771329").rules.size }
        probe("community posts") { client.communityPosts("1472249315724771329").threads.size }
        probe("trends") { client.trends(1).trends.size }
    }

    /**
     * What x.com's sign-in page links, written to the log and never failed on:
     * where the signing script is named when /home and /search are refused
     * (as they were to CI's runners from 3 November 2026).
     */
    @Test
    fun theSignInPageScriptsAreLogged() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live x.com")
        val http = OkHttpXHttp()
        val headers = mapOf(
            "Accept-Language" to "en-US,en;q=0.9",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
        )
        runCatching {
            val answer = http.get("https://x.com/i/flow/login".toHttpUrl(), headers, followRedirects = true)
            val page = Jsoup.parse(answer.body, "https://x.com/i/flow/login")
            val scripts = page.select("script[src]").map { it.attr("src") }
            val inline = page.select("script:not([src])").map { it.data() }
            println("LIVE sign-in page: HTTP ${answer.code}, ${answer.body.length} characters, ${scripts.size} scripts, ${inline.size} inline")
            println("LIVE sign-in inline scripts naming ondemand.s: ${inline.count { "ondemand.s" in it }}")
            scripts.take(12).forEach { src ->
                val source = runCatching { http.get(src.toHttpUrl(), emptyMap(), followRedirects = false).body }.getOrDefault("")
                println("LIVE sign-in script ${src.substringAfterLast('/')}: ${source.length} characters, ondemand.s ${"ondemand.s" in source}")
            }
        }.onFailure { println("LIVE sign-in page probe failed: ${it::class.simpleName}: ${it.message?.take(160)}") }
    }

    private suspend fun probe(what: String, read: suspend () -> Int) {
        val outcome = runCatching { read() }.fold({ "$it threads" }, { "refused, ${it::class.simpleName}: ${it.message?.take(160)}" })
        println("LIVE guest $what: $outcome")
    }
}
