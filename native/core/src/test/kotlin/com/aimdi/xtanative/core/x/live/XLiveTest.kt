package com.aimdi.xtanative.core.x.live

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.signing.SigningBootstrap
import com.aimdi.xtanative.core.x.signing.TransactionSigner
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
            println("LIVE sign-in title: ${page.title().take(80)}; noscript: ${page.select("noscript").text().take(120)}")
            inline.forEachIndexed { i, body ->
                val urls = Regex("""https?://[^"'\s]+?\.js""").findAll(body).map { it.value.substringAfterLast('/') }.distinct().take(6).toList()
                println("LIVE sign-in inline $i: ${body.length} characters, starts ${body.trim().take(60).replace(Regex("\\s+"), " ")}, scripts named $urls")
            }
            scripts.take(12).forEach { src ->
                val source = runCatching { http.get(src.toHttpUrl(), emptyMap(), followRedirects = false).body }.getOrDefault("")
                println("LIVE sign-in script ${src.substringAfterLast('/')}: ${source.length} characters, ondemand.s ${"ondemand.s" in source}")
            }
            walkBundles(http, inline)
            val jf = http.get("https://x.com/i/jf/".toHttpUrl(), headers, followRedirects = false)
            val jfPage = Jsoup.parse(jf.body, "https://x.com/i/jf/")
            println(
                "LIVE /i/jf/: HTTP ${jf.code}, ${jf.body.length} characters, lacks ${TransactionSigner.missingBootstrapData(jfPage) ?: "nothing"}, " +
                    "chunk map signer ${SigningBootstrap.chunkMapSigner(jf.body)?.encodedPath}",
            )
        }.onFailure { println("LIVE sign-in page probe failed: ${it::class.simpleName}: ${it.message?.take(160)}") }
        // A test returns nothing: JUnit passes over a method that returns the Result above.
        Unit
    }

    /**
     * The bundles the sign-in page's inline code starts, and their imports, as
     * the signer search walks them (breadth first, 24 at most): what each is
     * called, how big, what it imports, and whether it names a signing script,
     * holds the transaction-id header or the `(a[N], 16)` calls the indices
     * come from.
     */
    private suspend fun walkBundles(http: OkHttpXHttp, inline: List<String>) {
        val page = "https://x.com/".toHttpUrl()
        val literal = Regex("""(["'`])([^"'`\s$]+\.js)\1""")
        val imports = Regex("""(?:\bfrom\s*|\bimport\s*\(?\s*)(["'`])([^"'`\s$]+\.js)\1""")
        val calls = Regex("""\(\w\[\d{1,2}\],\s*16\)""")
        fun named(parent: okhttp3.HttpUrl, source: String, pattern: Regex) =
            pattern.findAll(source).mapNotNull { parent.resolve(it.groupValues[2]) }
                .filter { it.host == "abs.twimg.com" }.distinct().toList()
        val queue = ArrayDeque(inline.flatMap { named(page, it, literal) }.distinct())
        val seen = HashSet(queue)
        var read = 0
        while (queue.isNotEmpty() && read < 24) {
            val url = queue.removeFirst()
            read++
            val source = runCatching { http.get(url, emptyMap(), followRedirects = false).body }.getOrDefault("")
            val next = named(url, source, imports)
            val signers = named(url, source, literal).map { it.pathSegments.last() }
                .filter { it.startsWith("sign.") || it.startsWith("ondemand.") }
            println(
                "LIVE bundle ${url.encodedPath}: ${source.length} characters, ${next.size} imports, " +
                    "signers $signers, transaction header ${"x-client-transaction-id" in source}, " +
                    "(a[N], 16) calls ${calls.findAll(source).count()}",
            )
            next.filter { seen.add(it) }.forEach(queue::addLast)
        }
    }

    private suspend fun probe(what: String, read: suspend () -> Int) {
        val outcome = runCatching { read() }.fold({ "$it threads" }, { "refused, ${it::class.simpleName}: ${it.message?.take(160)}" })
        println("LIVE guest $what: $outcome")
    }
}
